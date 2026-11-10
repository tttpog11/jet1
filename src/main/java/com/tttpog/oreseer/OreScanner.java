package com.tttpog.oreseer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Keeps a per-chunk cache of predicted blobs around the player (computed off-thread). */
public final class OreScanner {
    private static final ConcurrentHashMap<Long, List<OrePredictor.Blob>> CACHE = new ConcurrentHashMap<>();
    private static final Set<Long> PENDING = ConcurrentHashMap.newKeySet();
    private static final ExecutorService POOL = Executors.newFixedThreadPool(Math.max(2, Math.min(4, Runtime.getRuntime().availableProcessors() - 1)), r -> {
        Thread t = new Thread(r, "OreSeer-scan");
        t.setDaemon(true);
        return t;
    });

    private static volatile int generation;
    private static volatile String lastSignature = "";
    private static int[][] offsets = new int[0][];
    private static int offsetsRadius = -1;
    private static int ticks;

    /** Nearest blobs of the enabled ores, closest first (refreshed every few ticks). */
    public static volatile List<OrePredictor.Blob> visible = List.of();
    public static volatile String error = null;
    public static volatile int matched;
    public static volatile int checked;
    public static volatile boolean wrongDimension;

    private OreScanner() {}

    public static int cachedChunks() {
        return CACHE.size();
    }

    public static int pendingChunks() {
        return PENDING.size();
    }

    public static void tick(Minecraft mc) {
        OreConfig c = OreConfig.get();
        ClientLevel level = mc.level;
        LocalPlayer player = mc.player;
        boolean nether = level != null && level.dimension() == Level.NETHER;
        wrongDimension = level != null && !nether && level.dimension() != Level.OVERWORLD;
        if (level == null || player == null || !c.finderEnabled || wrongDimension) {
            if (!visible.isEmpty()) visible = List.of();
            return;
        }

        final EnumSet<OreType> wanted = c.wantedTypes(nether);
        StringBuilder sb = new StringBuilder().append(nether).append('|').append(c.seed);
        for (OreType t : wanted) sb.append('|').append(t.id);
        String sig = sb.toString();
        if (!sig.equals(lastSignature)) {
            lastSignature = sig;
            generation++;
            CACHE.clear();
            PENDING.clear();
            error = null;
            matched = 0;
            checked = 0;
        }

        ticks++;
        int pcx = player.getBlockX() >> 4;
        int pcz = player.getBlockZ() >> 4;
        int radius = c.radius;

        // queue missing chunks, nearest first
        if (offsetsRadius != radius) {
            offsets = buildOffsets(radius);
            offsetsRadius = radius;
        }
        final int gen = generation;
        final long seed = c.seed;
        int submitted = 0;
        for (int[] o : offsets) {
            if (PENDING.size() >= 64 || submitted >= 24) break;
            int cx = pcx + o[0];
            int cz = pcz + o[1];
            long key = ChunkPos.asLong(cx, cz);
            if (CACHE.containsKey(key) || !PENDING.add(key)) continue;
            submitted++;
            POOL.execute(() -> {
                try {
                    if (gen != generation) return;
                    List<OrePredictor.Blob> result = OrePredictor.simulate(nether, seed, cx, cz, wanted, level);
                    if (gen == generation) CACHE.put(key, result);
                } catch (Throwable t) {
                    error = String.valueOf(t.getMessage() != null ? t.getMessage() : t);
                    if (gen == generation) CACHE.put(key, List.of());
                    t.printStackTrace();
                } finally {
                    PENDING.remove(key);
                }
            });
        }

        if (ticks % 20 == 0) {
            CACHE.keySet().removeIf(k -> Math.abs(ChunkPos.getX(k) - pcx) > radius + 2
                    || Math.abs(ChunkPos.getZ(k) - pcz) > radius + 2);
        }

        if (ticks % 5 == 0) rebuildVisible(player, level, c, wanted, radius);
        if (ticks % 20 == 0) checkAccuracy(level);
    }

    private static void rebuildVisible(LocalPlayer player, ClientLevel level, OreConfig c, EnumSet<OreType> wanted, int radius) {
        Vec3 eye = player.getEyePosition();
        double max = (radius * 16.0 + 16.0);
        double max2 = max * max;
        List<OrePredictor.Blob> all = new ArrayList<>();
        for (List<OrePredictor.Blob> list : CACHE.values()) {
            for (OrePredictor.Blob b : list) {
                if (!wanted.contains(b.type())) continue;
                if (b.y() < c.minY || b.y() > c.maxY) continue;
                double dx = b.x() - eye.x, dy = b.y() - eye.y, dz = b.z() - eye.z;
                if (dx * dx + dy * dy + dz * dz <= max2 && looksReal(level, b)) all.add(b);
            }
        }
        all.sort(Comparator.comparingDouble(b -> {
            double dx = b.x() - eye.x, dy = b.y() - eye.y, dz = b.z() - eye.z;
            return dx * dx + dy * dy + dz * dz;
        }));
        visible = all.size() > 600 ? new ArrayList<>(all.subList(0, 600)) : all;
    }

    /**
     * Drops veins that are provably not there: if the client has the chunk loaded and the sampled
     * blocks are all air (high-altitude coal over the surface, mined-out ore, caves), hide it.
     */
    private static boolean looksReal(ClientLevel level, OrePredictor.Blob b) {
        long[] blocks = b.blocks();
        int[] sample = {0, blocks.length / 2, blocks.length - 1};
        int loaded = 0;
        for (int i : sample) {
            long l = blocks[i];
            if (!level.hasChunk(BlockPos.getX(l) >> 4, BlockPos.getZ(l) >> 4)) continue;
            loaded++;
            if (!level.getBlockState(BlockPos.of(l)).isAir()) return true;
        }
        return loaded == 0;
    }

    /** Compares predicted blocks against what the client really has loaded. */
    private static void checkAccuracy(ClientLevel level) {
        List<OrePredictor.Blob> list = visible;
        int ok = 0, total = 0;
        int looked = 0;
        for (OrePredictor.Blob b : list) {
            if (looked >= 150) break;
            boolean any = false;
            for (long l : b.blocks()) {
                int x = BlockPos.getX(l), z = BlockPos.getZ(l);
                if (!level.hasChunk(x >> 4, z >> 4)) continue;
                any = true;
                BlockState s = level.getBlockState(BlockPos.of(l));
                var key = BuiltInRegistries.BLOCK.getKey(s.getBlock());
                if (OreType.fromBlockPath(key.getPath()) == b.type()) ok++;
                total++;
            }
            if (any) looked++;
        }
        matched = ok;
        checked = total;
    }

    private static int[][] buildOffsets(int r) {
        List<int[]> list = new ArrayList<>();
        for (int x = -r; x <= r; x++) {
            for (int z = -r; z <= r; z++) {
                if (x * x + z * z <= (r + 0.5) * (r + 0.5)) list.add(new int[]{x, z});
            }
        }
        list.sort(Comparator.comparingInt(o -> o[0] * o[0] + o[1] * o[1]));
        return list.toArray(new int[0][]);
    }
}
