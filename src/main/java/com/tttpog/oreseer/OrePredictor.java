package com.tttpog.oreseer;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.FeatureSorter;
import net.minecraft.world.level.biome.OverworldBiomeBuilder;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.placement.PlacementModifier;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Re-runs vanilla's ore decoration (overworld + nether) for a chunk from the world seed.
 *
 * Feature order, ore sizes and placement modifiers are read from vanilla's own worldgen data
 * (VanillaRegistries), and the random calls follow ChunkGenerator#applyBiomeDecoration,
 * OreFeature#place and ScatteredOreFeature#place so the same seed lands the same blobs.
 *
 * Known approximations (terrain is not simulated):
 *  - every block is assumed to sit in solid rock (no caves, no air exposure),
 *  - the "is the whole blob above the surface" height check is skipped,
 *  - biome-restricted features are only evaluated where the chunk is loaded.
 */
public final class OrePredictor {
    // decoration steps that hold ore features: UNDERGROUND_ORES (6) and UNDERGROUND_DECORATION (7)
    private static final int[] STEPS = {6, 7};

    /** One predicted ore vein: centroid plus every block position (BlockPos#asLong). */
    public record Blob(OreType type, double x, double y, double z, long[] blocks) {
        public int count() {
            return blocks.length;
        }
    }

    private sealed interface Mod permits Count, InSquare, Height, Rarity, BiomeCheck {}

    private record Count(IntProvider provider) implements Mod {}

    private record InSquare() implements Mod {}

    private record Height(JsonObject json) implements Mod {}

    private record Rarity(int chance) implements Mod {}

    private record BiomeCheck() implements Mod {}

    private record Gen(int step, int index, boolean scattered, OreType stone, OreType deep, int size,
                       float discard, List<Mod> mods, boolean universal, PlacedFeature feature) {}

    private record Ctx(int minY, int maxY, List<Gen> gens) {}

    private static HolderLookup.Provider lookup;
    private static HolderLookup<Biome> biomeLookup;
    private static Ctx overworld;
    private static Ctx nether;

    private OrePredictor() {}

    private static synchronized Ctx ctx(boolean isNether) {
        try {
            if (lookup == null) {
                lookup = VanillaRegistries.createLookup();
                biomeLookup = lookup.lookupOrThrow(Registries.BIOME);
            }
            if (isNether) {
                if (nether == null) nether = build(true);
                return nether;
            }
            if (overworld == null) overworld = build(false);
            return overworld;
        } catch (Throwable t) {
            throw new IllegalStateException("init failed: " + t, t);
        }
    }

    private static Ctx build(boolean isNether) throws Exception {
        int minY = isNether ? 0 : -64;
        int maxY = isNether ? 255 : 319;

        LinkedHashSet<ResourceKey<Biome>> keys = new LinkedHashSet<>();
        if (isNether) {
            keys.add(Biomes.NETHER_WASTES);
            keys.add(Biomes.SOUL_SAND_VALLEY);
            keys.add(Biomes.CRIMSON_FOREST);
            keys.add(Biomes.WARPED_FOREST);
            keys.add(Biomes.BASALT_DELTAS);
        } else {
            // addBiomes is made public by oreseer.accesswidener (same order as the overworld biome source)
            new OverworldBiomeBuilder().addBiomes(pair -> keys.add(pair.getSecond()));
        }
        List<Holder<Biome>> biomes = new ArrayList<>();
        for (ResourceKey<Biome> k : keys) biomes.add(biomeLookup.getOrThrow(k));

        List<FeatureSorter.StepFeatureData> steps = FeatureSorter.buildFeaturesPerStep(
                biomes, b -> b.value().getGenerationSettings().features(), false);

        List<Gen> out = new ArrayList<>();
        for (int step : STEPS) {
            if (step >= steps.size()) continue;
            List<PlacedFeature> features = steps.get(step).features();
            for (int i = 0; i < features.size(); i++) {
                Gen g = parse(step, i, features.get(i), biomes);
                if (g != null) out.add(g);
            }
        }
        return new Ctx(minY, maxY, out);
    }

    private static Gen parse(int step, int index, PlacedFeature pf, List<Holder<Biome>> biomes) {
        try {
            ConfiguredFeature<?, ?> cf = pf.feature().value();
            Optional<JsonElement> enc = ConfiguredFeature.DIRECT_CODEC.encodeStart(JsonOps.INSTANCE, cf).result();
            if (enc.isEmpty()) return null;
            JsonObject root = enc.get().getAsJsonObject();
            String ftype = root.get("type").getAsString();
            boolean scattered;
            if ("minecraft:ore".equals(ftype)) scattered = false;
            else if ("minecraft:scattered_ore".equals(ftype)) scattered = true;
            else return null;

            JsonObject cfg = root.getAsJsonObject("config");
            int size = cfg.get("size").getAsInt();
            float discard = cfg.has("discard_chance_on_air_exposure")
                    ? cfg.get("discard_chance_on_air_exposure").getAsFloat() : 0f;

            OreType stone = null, deep = null;
            JsonArray targets = cfg.getAsJsonArray("targets");
            for (JsonElement t : targets) {
                String name = t.getAsJsonObject().getAsJsonObject("state").get("Name").getAsString();
                OreType type = OreType.fromBlockPath(name.substring(name.indexOf(':') + 1));
                if (type == null) continue;
                if (type.group == OreType.Group.DEEPSLATE) {
                    if (deep == null) deep = type;
                } else if (stone == null) {
                    stone = type;
                }
            }
            if (stone == null && deep == null) return null;

            List<Mod> mods = new ArrayList<>();
            for (PlacementModifier m : pf.placement()) {
                JsonObject o = PlacementModifier.CODEC.encodeStart(JsonOps.INSTANCE, m).result()
                        .orElseThrow().getAsJsonObject();
                String mt = o.get("type").getAsString().replace("minecraft:", "");
                switch (mt) {
                    case "count" -> mods.add(new Count(IntProvider.CODEC.parse(JsonOps.INSTANCE, o.get("count"))
                            .result().orElseThrow()));
                    case "in_square" -> mods.add(new InSquare());
                    case "height_range" -> mods.add(new Height(o.getAsJsonObject("height")));
                    case "rarity_filter" -> mods.add(new Rarity(o.get("chance").getAsInt()));
                    case "biome" -> mods.add(new BiomeCheck());
                    default -> {
                        return null; // unsupported placement, skip this feature
                    }
                }
            }

            boolean universal = true;
            for (Holder<Biome> b : biomes) {
                if (!b.value().getGenerationSettings().hasFeature(pf)) {
                    universal = false;
                    break;
                }
            }
            return new Gen(step, index, scattered, stone, deep, size, discard, mods, universal, pf);
        } catch (Throwable t) {
            return null;
        }
    }

    /** Everything one chunk simulation needs. */
    private static final class Run {
        final Ctx ctx;
        final WorldgenRandom rnd;
        final List<Blob> out = new ArrayList<>();
        final EnumSet<OreType> wanted;
        final Level level;

        Run(Ctx ctx, WorldgenRandom rnd, EnumSet<OreType> wanted, Level level) {
            this.ctx = ctx;
            this.rnd = rnd;
            this.wanted = wanted;
            this.level = level;
        }
    }

    /** Predicts the ore veins whose decoration origin chunk is (cx, cz). */
    public static List<Blob> simulate(boolean isNether, long seed, int cx, int cz, EnumSet<OreType> wanted, Level level) {
        Ctx ctx = ctx(isNether);
        WorldgenRandom rnd = new WorldgenRandom(new XoroshiroRandomSource(0L));
        long decoration = rnd.setDecorationSeed(seed, cx << 4, cz << 4);
        Run run = new Run(ctx, rnd, wanted, level);
        for (Gen g : ctx.gens()) {
            if (!(wanted.contains(g.stone()) || wanted.contains(g.deep()))) continue;
            rnd.setFeatureSeed(decoration, g.index(), g.step());
            apply(g, 0, cx << 4, ctx.minY(), cz << 4, run);
        }
        return run.out;
    }

    // Mirrors the lazily flat-mapped Stream in PlacedFeature#placeWithContext: each position is
    // pushed through the remaining modifiers (and placed) before the next one is generated.
    private static void apply(Gen g, int mi, int x, int y, int z, Run run) {
        WorldgenRandom r = run.rnd;
        if (mi == g.mods().size()) {
            place(g, x, y, z, run);
            return;
        }
        Mod m = g.mods().get(mi);
        switch (m) {
            case Count c -> {
                int n = c.provider().sample(r);
                for (int i = 0; i < n; i++) apply(g, mi + 1, x, y, z, run);
            }
            case InSquare ignored -> {
                int nx = r.nextInt(16) + x;
                int nz = r.nextInt(16) + z;
                apply(g, mi + 1, nx, y, nz, run);
            }
            case Height h -> apply(g, mi + 1, x, sampleHeight(h.json(), r, run.ctx), z, run);
            case Rarity ra -> {
                if (r.nextFloat() < 1.0F / (float) ra.chance()) apply(g, mi + 1, x, y, z, run);
            }
            case BiomeCheck ignored -> {
                if (biomeAllows(g, x, y, z, run.level)) apply(g, mi + 1, x, y, z, run);
            }
        }
    }

    private static boolean biomeAllows(Gen g, int x, int y, int z, Level level) {
        if (g.universal()) return true;
        try {
            if (level == null || !level.hasChunk(x >> 4, z >> 4)) return false;
            Optional<ResourceKey<Biome>> key = level.getBiome(new BlockPos(x, y, z)).unwrapKey();
            if (key.isEmpty()) return false;
            Optional<Holder.Reference<Biome>> b = biomeLookup.get(key.get());
            return b.isPresent() && b.get().value().getGenerationSettings().hasFeature(g.feature());
        } catch (Throwable t) {
            return false;
        }
    }

    // ---- height providers (mirrors the vanilla classes, anchors resolved per dimension) ----

    private static int anchor(JsonElement e, Ctx c) {
        JsonObject o = e.getAsJsonObject();
        if (o.has("absolute")) return o.get("absolute").getAsInt();
        if (o.has("above_bottom")) return c.minY() + o.get("above_bottom").getAsInt();
        if (o.has("below_top")) return c.maxY() - o.get("below_top").getAsInt();
        throw new IllegalArgumentException("anchor " + o);
    }

    private static int sampleHeight(JsonObject h, RandomSource r, Ctx c) {
        String type = h.get("type").getAsString().replace("minecraft:", "");
        switch (type) {
            case "constant":
                return anchor(h.get("value"), c);
            case "uniform": {
                int lo = anchor(h.get("min_inclusive"), c);
                int hi = anchor(h.get("max_inclusive"), c);
                if (lo > hi) return lo;
                return Mth.randomBetweenInclusive(r, lo, hi);
            }
            case "trapezoid": {
                int lo = anchor(h.get("min_inclusive"), c);
                int hi = anchor(h.get("max_inclusive"), c);
                int plateau = h.has("plateau") ? h.get("plateau").getAsInt() : 0;
                if (lo > hi) return lo;
                int k = hi - lo;
                if (plateau >= k) return Mth.randomBetweenInclusive(r, lo, hi);
                int l = (k - plateau) / 2;
                int i1 = k - l;
                return lo + Mth.randomBetweenInclusive(r, 0, i1) + Mth.randomBetweenInclusive(r, 0, l);
            }
            case "biased_to_bottom": {
                int lo = anchor(h.get("min_inclusive"), c);
                int hi = anchor(h.get("max_inclusive"), c);
                int inner = h.has("inner") ? h.get("inner").getAsInt() : 1;
                if (hi - lo - inner + 1 <= 0) return lo;
                int k = r.nextInt(hi - lo - inner + 1);
                return r.nextInt(k + inner) + lo;
            }
            case "very_biased_to_bottom": {
                int lo = anchor(h.get("min_inclusive"), c);
                int hi = anchor(h.get("max_inclusive"), c);
                int inner = h.has("inner") ? h.get("inner").getAsInt() : 1;
                if (hi - lo - inner + 1 <= 0) return lo;
                int k = Mth.randomBetweenInclusive(r, lo + inner, hi);
                int l = Mth.randomBetweenInclusive(r, lo, k - 1);
                return Mth.randomBetweenInclusive(r, lo, l - 1 + inner);
            }
            default:
                throw new UnsupportedOperationException("height provider " + type);
        }
    }

    // ---- feature placement ----

    private static void place(Gen g, int ox, int oy, int oz, Run run) {
        List<Long> blocks = g.scattered() ? scatter(g, run, ox, oy, oz) : blob(g, run, ox, oy, oz);
        if (blocks.isEmpty()) return;

        // split into stone / deepslate variants (deepslate below y = 0)
        Map<OreType, List<Long>> buckets = new EnumMap<>(OreType.class);
        for (long l : blocks) {
            OreType t;
            if (g.deep() != null && BlockPos.getY(l) < 0) t = g.deep();
            else t = g.stone() != null ? g.stone() : g.deep();
            buckets.computeIfAbsent(t, k -> new ArrayList<>()).add(l);
        }
        for (Map.Entry<OreType, List<Long>> e : buckets.entrySet()) {
            if (!run.wanted.contains(e.getKey())) continue;
            List<Long> list = e.getValue();
            long[] arr = new long[list.size()];
            double sx = 0, sy = 0, sz = 0;
            for (int n = 0; n < arr.length; n++) {
                long p = list.get(n);
                arr[n] = p;
                sx += BlockPos.getX(p) + 0.5;
                sy += BlockPos.getY(p) + 0.5;
                sz += BlockPos.getZ(p) + 0.5;
            }
            run.out.add(new Blob(e.getKey(), sx / arr.length, sy / arr.length, sz / arr.length, arr));
        }
    }

    /** ScatteredOreFeature#place (ancient debris). */
    private static List<Long> scatter(Gen g, Run run, int ox, int oy, int oz) {
        WorldgenRandom r = run.rnd;
        LinkedHashSet<Long> set = new LinkedHashSet<>();
        int n = r.nextInt(g.size() + 1);
        for (int j = 0; j < n; j++) {
            int spread = Math.min(j, 7);
            int dx = Math.round((r.nextFloat() - r.nextFloat()) * (float) spread);
            int dy = Math.round((r.nextFloat() - r.nextFloat()) * (float) spread);
            int dz = Math.round((r.nextFloat() - r.nextFloat()) * (float) spread);
            int y = oy + dy;
            if (y < run.ctx.minY() || y > run.ctx.maxY()) continue;
            set.add(BlockPos.asLong(ox + dx, y, oz + dz));
        }
        return new ArrayList<>(set);
    }

    /** OreFeature#place / #doPlace. */
    private static List<Long> blob(Gen g, Run run, int ox, int oy, int oz) {
        WorldgenRandom r = run.rnd;
        int minBuild = run.ctx.minY();
        int maxBuild = run.ctx.maxY();
        int size = g.size();
        float f = r.nextFloat() * (float) Math.PI;
        float f1 = (float) size / 8.0F;
        int i = Mth.ceil(((float) size / 16.0F * 2.0F + 1.0F) / 2.0F);
        double d0 = (double) ox + Math.sin((double) f) * (double) f1;
        double d1 = (double) ox - Math.sin((double) f) * (double) f1;
        double d2 = (double) oz + Math.cos((double) f) * (double) f1;
        double d3 = (double) oz - Math.cos((double) f) * (double) f1;
        double d4 = (double) (oy + r.nextInt(3) - 2);
        double d5 = (double) (oy + r.nextInt(3) - 2);
        int minX = ox - Mth.ceil(f1) - i;
        int minY = oy - 2 - i;
        int minZ = oz - Mth.ceil(f1) - i;
        int horiz = 2 * (Mth.ceil(f1) + i);
        int vert = 2 * (2 + i);

        BitSet seen = new BitSet(horiz * vert * horiz);
        double[] a = new double[size * 4];
        for (int k = 0; k < size; k++) {
            float t = (float) k / (float) size;
            double x = Mth.lerp((double) t, d0, d1);
            double y = Mth.lerp((double) t, d4, d5);
            double z = Mth.lerp((double) t, d2, d3);
            double w = r.nextDouble() * (double) size / 16.0D;
            double rad = ((double) (Mth.sin((float) Math.PI * t) + 1.0F) * w + 1.0D) / 2.0D;
            a[k * 4] = x;
            a[k * 4 + 1] = y;
            a[k * 4 + 2] = z;
            a[k * 4 + 3] = rad;
        }

        for (int l = 0; l < size - 1; l++) {
            if (!(a[l * 4 + 3] <= 0.0D)) {
                for (int m = l + 1; m < size; m++) {
                    if (!(a[m * 4 + 3] <= 0.0D)) {
                        double dx = a[l * 4] - a[m * 4];
                        double dy = a[l * 4 + 1] - a[m * 4 + 1];
                        double dz = a[l * 4 + 2] - a[m * 4 + 2];
                        double dr = a[l * 4 + 3] - a[m * 4 + 3];
                        if (dr * dr > dx * dx + dy * dy + dz * dz) {
                            if (dr > 0.0D) a[m * 4 + 3] = -1.0D;
                            else a[l * 4 + 3] = -1.0D;
                        }
                    }
                }
            }
        }

        List<Long> blocks = new ArrayList<>();
        boolean rollAir = g.discard() > 0.0F && g.discard() < 1.0F;
        for (int j = 0; j < size; j++) {
            double rad = a[j * 4 + 3];
            if (rad < 0.0D) continue;
            double cx = a[j * 4];
            double cy = a[j * 4 + 1];
            double cz = a[j * 4 + 2];
            int x0 = Math.max(Mth.floor(cx - rad), minX);
            int y0 = Math.max(Mth.floor(cy - rad), minY);
            int z0 = Math.max(Mth.floor(cz - rad), minZ);
            int x1 = Math.max(Mth.floor(cx + rad), x0);
            int y1 = Math.max(Mth.floor(cy + rad), y0);
            int z1 = Math.max(Mth.floor(cz + rad), z0);
            for (int px = x0; px <= x1; px++) {
                double nx = ((double) px + 0.5D - cx) / rad;
                if (nx * nx >= 1.0D) continue;
                for (int py = y0; py <= y1; py++) {
                    double ny = ((double) py + 0.5D - cy) / rad;
                    if (nx * nx + ny * ny >= 1.0D) continue;
                    for (int pz = z0; pz <= z1; pz++) {
                        double nz = ((double) pz + 0.5D - cz) / rad;
                        if (nx * nx + ny * ny + nz * nz < 1.0D && py >= minBuild && py <= maxBuild) {
                            int idx = px - minX + (py - minY) * horiz + (pz - minZ) * horiz * vert;
                            if (!seen.get(idx)) {
                                seen.set(idx);
                                // canPlaceOre: the air-exposure roll happens once per valid block
                                if (rollAir) r.nextFloat();
                                blocks.add(BlockPos.asLong(px, py, pz));
                            }
                        }
                    }
                }
            }
        }
        return blocks;
    }
}
