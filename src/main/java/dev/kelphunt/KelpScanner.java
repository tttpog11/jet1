package dev.kelphunt;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reads kelp ages from chunks the client has already received. It never asks the server for
 * anything and never looks at anything the game isn't already showing you.
 */
public final class KelpScanner {
    private static final int CHUNKS_PER_TICK = 6;
    private static final int MIN_HEADS = 4;
    /** Columns sticking out of the water before a chunk counts as having above-water blocks. */
    private static final int ABOVE_COLUMNS = 3;
    /** Abrupt (3+ block) seabed steps, out of 480 neighbouring column pairs, before the seabed counts as rough. */
    private static final int ROUGH_STEPS = 40;
    private static final int ABOVE_BONUS = 15;
    private static final int ROUGH_BONUS = 10;
    private static final int MAX_CLUSTERS = 20;
    /** Budding amethyst blocks / open faces needed before a chunk's geode is judged. */
    private static final int MIN_BUDDING = 3;
    private static final int MIN_OPEN_FACES = 6;
    /** Cave bonus: 0 at 4% open air, full at 14%. A guess; natural cave-rich areas also score. */
    private static final double CAVE_FLOOR = 0.04, CAVE_SPAN = 0.10;
    private static final int CAVE_BONUS_MAX = 15;
    private static final boolean HAS_XAEROPLUS = FabricLoader.getInstance().isModLoaded("xaeroplus");
    /** Added to the kelp score for a chunk XaeroPlus lists as old. */
    private static final int OLD_CHUNK_BONUS = 20;
    /** A newly generated chunk can't have been loaded for long, so its score is scaled down. */
    private static final double NEW_CHUNK_FACTOR = 0.25;

    /** Waypoints are only made for clusters scoring at least this. */
    public int minWaypointScore = 10;
    /** Skip chunks inside your own simulation distance: you are loading those yourself, so grown kelp there proves nothing. */
    public boolean skipNear = false;
    /** Also use seabed/above-water shape as extra evidence (cheap, vanilla heightmap data). */
    public boolean terrain = false;
    /** Read budding amethyst growth in loaded chunks (geodes are underground; buds only grow while loaded). */
    public boolean amethyst = true;
    private int skippedNear;
    public int skippedNear() { return skippedNear; }

    private final ArrayDeque<ChunkPos> queue = new ArrayDeque<>();
    /** Everything measured so far. Kept across scans so the baseline grows as you explore. */
    private final Map<ChunkPos, ChunkReport> reports = new HashMap<>();
    private boolean running;
    private boolean finishedFlag;

    public boolean isRunning() { return running; }
    public int remaining() { return queue.size(); }
    public int measuredChunks() { return reports.size(); }

    /** True exactly once after a scan finishes. */
    public boolean pollFinished() {
        boolean f = finishedFlag;
        finishedFlag = false;
        return f;
    }

    public void start(int radiusChunks) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        queue.clear();
        skippedNear = 0;
        ChunkPos c = mc.player.chunkPosition();
        for (int dx = -radiusChunks; dx <= radiusChunks; dx++) {
            for (int dz = -radiusChunks; dz <= radiusChunks; dz++) {
                queue.add(new ChunkPos(c.x + dx, c.z + dz));
            }
        }
        running = true;
    }

    public void clearAll() {
        queue.clear();
        reports.clear();
        running = false;
        finishedFlag = false;
        KelpData.clear();
    }

    public void tick() {
        if (!running) return;
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) { clearAll(); return; }

        for (int i = 0; i < CHUNKS_PER_TICK && !queue.isEmpty(); i++) {
            ChunkPos pos = queue.poll();
            LevelChunk chunk = level.getChunkSource().getChunk(pos.x, pos.z, ChunkStatus.FULL, false);
            if (chunk == null) continue;
            ChunkReport r = scanChunk(level, chunk, pos);
            if (r != null) reports.put(pos, r);
        }
        if (queue.isEmpty()) {
            running = false;
            finish();
        }
    }

    private ChunkReport scanChunk(ClientLevel level, LevelChunk chunk, ChunkPos pos) {
        Minecraft mcNow = Minecraft.getInstance();
        if (skipNear && mcNow.player != null) {
            ChunkPos me = mcNow.player.chunkPosition();
            int sim = mcNow.options.simulationDistance().get();
            if (Math.max(Math.abs(pos.x - me.x), Math.abs(pos.z - me.z)) <= sim) {
                skippedNear++;
                return null;
            }
        }
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        int top = level.getSeaLevel();
        p.set(pos.getMiddleBlockX(), top - 8, pos.getMiddleBlockZ());
        boolean ocean = level.getBiome(p).is(BiomeTags.IS_OCEAN);

        int bottom = Math.max(level.getMinY(), top - 45);
        int heads = 0, done = 0;
        long ySum = 0;

        for (int x = 0; ocean && x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = top; y >= bottom; y--) {
                    p.set(pos.getMinBlockX() + x, y, pos.getMinBlockZ() + z);
                    BlockState s = chunk.getBlockState(p);
                    if (!s.is(Blocks.KELP)) continue;
                    int age = s.getValue(BlockStateProperties.AGE_25);
                    heads++;
                    ySum += y;
                    boolean capped = !level.getFluidState(p.above()).is(FluidTags.WATER);
                    if (age >= 25 || capped) done++;
                    break; // the first kelp block from the top is the growing head
                }
            }
        }

        int above = 0;
        boolean rough = false;
        int floorMean = top - 20;
        if (terrain && ocean) {
            int[] floor = new int[256];
            long fsum = 0;
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    int fh = chunk.getHeight(Heightmap.Types.OCEAN_FLOOR, x, z);
                    floor[x * 16 + z] = fh;
                    fsum += fh;
                    int sh = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
                    if (sh > top + 1) {
                        p.set(pos.getMinBlockX() + x, sh - 1, pos.getMinBlockZ() + z);
                        BlockState s = chunk.getBlockState(p);
                        // icebergs and snow are natural, don't count them
                        if (!s.is(BlockTags.ICE) && !s.is(Blocks.SNOW) && !s.is(Blocks.SNOW_BLOCK) && !s.is(Blocks.POWDER_SNOW)) above++;
                    }
                }
            }
            floorMean = (int) (fsum / 256);
            int steps = 0;
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    int h = floor[x * 16 + z];
                    if (x < 15 && Math.abs(h - floor[(x + 1) * 16 + z]) >= 3) steps++;
                    if (z < 15 && Math.abs(h - floor[x * 16 + z + 1]) >= 3) steps++;
                }
            }
            rough = steps >= ROUGH_STEPS;
        }
        boolean hasKelp = heads >= MIN_HEADS;
        boolean anomaly = above >= ABOVE_COLUMNS || rough;
        Amethyst am = amethyst ? scanAmethyst(level, chunk, pos) : null;
        if (!hasKelp && !anomaly && am == null) return null;
        boolean old = false, fresh = false;
        if (HAS_XAEROPLUS) {
            old = XaeroPlusLink.isOld(pos.x, pos.z, level.dimension());
            fresh = XaeroPlusLink.isNew(pos.x, pos.z, level.dimension());
        }
        return new ChunkReport(pos, hasKelp ? heads : 0, hasKelp ? (double) done / heads : 0,
            hasKelp ? (int) (ySum / heads) : floorMean, 0, old, fresh, above, rough,
            am == null ? 0 : am.budding(), am == null ? 0 : am.fill(), am == null ? 0 : am.avgY(), am == null ? 0 : am.cavePct());
    }

    private record Amethyst(int budding, double fill, int avgY, int cavePct) {}

    private static int stage(BlockState s) {
        if (s.is(Blocks.SMALL_AMETHYST_BUD)) return 1;
        if (s.is(Blocks.MEDIUM_AMETHYST_BUD)) return 2;
        if (s.is(Blocks.LARGE_AMETHYST_BUD)) return 3;
        if (s.is(Blocks.AMETHYST_CLUSTER)) return 4;
        return 0;
    }

    /**
     * Budding amethyst grows its buds (small, medium, large, cluster) only while its chunk is loaded, and one
     * growth step takes tens of minutes, so full clusters mean hours of loading. Only sections whose block
     * palette contains budding amethyst are looked at. Also samples how much of the underground is open air.
     */
    private Amethyst scanAmethyst(ClientLevel level, LevelChunk chunk, ChunkPos pos) {
        LevelChunkSection[] sections = chunk.getSections();
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        int budding = 0, open = 0, fillSum = 0;
        long ySum = 0;
        for (int i = 0; i < sections.length; i++) {
            LevelChunkSection sec = sections[i];
            if (sec == null || sec.hasOnlyAir() || !sec.maybeHas(s -> s.is(Blocks.BUDDING_AMETHYST))) continue;
            int baseY = level.getSectionYFromSectionIndex(i) << 4;
            for (int x = 0; x < 16; x++) {
                for (int y = 0; y < 16; y++) {
                    for (int z = 0; z < 16; z++) {
                        if (!sec.getBlockState(x, y, z).is(Blocks.BUDDING_AMETHYST)) continue;
                        budding++;
                        ySum += baseY + y;
                        p.set(pos.getMinBlockX() + x, baseY + y, pos.getMinBlockZ() + z);
                        for (Direction d : Direction.values()) {
                            BlockState n = level.getBlockState(p.relative(d));
                            int st = stage(n);
                            if (st > 0 || n.isAir() || n.is(Blocks.WATER)) {
                                open++;
                                fillSum += st;
                            }
                        }
                    }
                }
            }
        }
        if (budding < MIN_BUDDING || open < MIN_OPEN_FACES) return null;

        int air = 0, total = 0;
        for (int y = level.getMinY(); y <= 50; y += 2) {
            for (int x = 0; x < 16; x += 2) {
                for (int z = 0; z < 16; z += 2) {
                    p.set(pos.getMinBlockX() + x, y, pos.getMinBlockZ() + z);
                    total++;
                    if (chunk.getBlockState(p).isAir()) air++;
                }
            }
        }
        return new Amethyst(budding, fillSum / (4.0 * open), (int) (ySum / budding),
            (int) Math.round(100.0 * air / Math.max(1, total)));
    }

    /**
     * Score = how many standard deviations a chunk's fully-grown fraction sits above the
     * ocean-wide average, scaled 0-100. At or below average scores 0.
     */
    private void finish() {
        finishedFlag = true;
        if (reports.isEmpty()) {
            KelpData.clear();
            return;
        }
        // Kelp baseline uses only chunks with enough kelp to judge.
        List<ChunkReport> kelpChunks = reports.values().stream().filter(r -> r.heads() >= MIN_HEADS).toList();
        boolean baseline = kelpChunks.size() >= 5;
        double mean = kelpChunks.stream().mapToDouble(ChunkReport::doneFraction).average().orElse(0);
        double var = kelpChunks.stream().mapToDouble(r -> Math.pow(r.doneFraction() - mean, 2)).average().orElse(0);
        double sd = Math.max(Math.sqrt(var), 0.05); // floor stops a uniform ocean from exploding

        // Amethyst: relative to other measured geodes when there are enough, otherwise an absolute scale.
        List<ChunkReport> geodes = reports.values().stream().filter(r -> r.budding() > 0).toList();
        boolean amBaseline = geodes.size() >= 5;
        double amMean = geodes.stream().mapToDouble(ChunkReport::amFill).average().orElse(0);
        double amVar = geodes.stream().mapToDouble(r -> Math.pow(r.amFill() - amMean, 2)).average().orElse(0);
        double amSd = Math.max(Math.sqrt(amVar), 0.05);

        Map<Long, ChunkReport> positives = new HashMap<>();
        for (Map.Entry<ChunkPos, ChunkReport> e : reports.entrySet()) {
            ChunkReport r = e.getValue();
            int kelpScore = 0;
            if (baseline && r.heads() >= MIN_HEADS) {
                double z = (r.doneFraction() - mean) / sd;
                kelpScore = (int) Math.round(Math.max(0, Math.min(100, z * 40)));
            }
            int amScore = 0;
            if (r.budding() > 0) {
                double v = amBaseline ? (r.amFill() - amMean) / amSd * 40 : (r.amFill() - 0.6) / 0.35 * 100;
                amScore = (int) Math.round(Math.max(0, Math.min(100, v)));
            }
            // Two independent kinds of "has been loaded a long time" evidence: the stronger counts fully, the other half.
            int score = Math.min(100, Math.max(kelpScore, amScore) + Math.min(kelpScore, amScore) / 2);
            if (r.budding() > 0) {
                double caves = (r.cavePct() / 100.0 - CAVE_FLOOR) / CAVE_SPAN * CAVE_BONUS_MAX;
                score += (int) Math.round(Math.max(0, Math.min(CAVE_BONUS_MAX, caves)));
            }
            if (r.above() >= ABOVE_COLUMNS) score += ABOVE_BONUS;
            if (r.rough()) score += ROUGH_BONUS;
            score = Math.min(100, score);
            if (e.getValue().fresh()) score = (int) Math.round(score * NEW_CHUNK_FACTOR);
            else if (e.getValue().old()) score = Math.min(100, score + OLD_CHUNK_BONUS);
            ChunkReport scored = e.getValue().withScore(score);
            e.setValue(scored);
            if (score > 0) positives.put(e.getKey().toLong(), scored);
        }
        KelpData.publish(positives, cluster(positives));
    }

    /** Flood-fills neighbouring positive chunks into clusters; one waypoint per cluster. */
    private List<Cluster> cluster(Map<Long, ChunkReport> positives) {
        Set<Long> seen = new HashSet<>();
        List<Cluster> out = new ArrayList<>();
        for (Long start : positives.keySet()) {
            if (!seen.add(start)) continue;
            ArrayDeque<Long> stack = new ArrayDeque<>();
            stack.push(start);
            List<ChunkReport> members = new ArrayList<>();
            while (!stack.isEmpty()) {
                long k = stack.pop();
                ChunkReport r = positives.get(k);
                members.add(r);
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dz == 0) continue;
                        long nk = ChunkPos.asLong(r.pos().x + dx, r.pos().z + dz);
                        if (positives.containsKey(nk) && seen.add(nk)) stack.push(nk);
                    }
                }
            }
            // Cluster score = best chunk, nudged up a little for bigger clusters.
            int best = members.stream().mapToInt(ChunkReport::score).max().orElse(0);
            int score = Math.min(100, best + Math.min(10, members.size() - 1));
            if (score < minWaypointScore) continue;
            // Centre on the strongest chunk so the waypoint lands on solid evidence.
            ChunkReport peak = members.stream().max(Comparator.comparingInt(ChunkReport::score)).orElseThrow();
            // A player parked underground keeps a square of chunks ticking around them, so the middle of the
            // strong patch points at them better than any single chunk does.
            int bx = peak.pos().getMiddleBlockX(), bz = peak.pos().getMiddleBlockZ();
            int spanX = 1, spanZ = 1;
            double cutoff = Math.max(minWaypointScore, best * 0.5);
            List<ChunkReport> strong = members.stream().filter(m -> m.score() >= cutoff).toList();
            if (strong.size() >= 4) {
                int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
                for (ChunkReport m : strong) {
                    minX = Math.min(minX, m.pos().x); maxX = Math.max(maxX, m.pos().x);
                    minZ = Math.min(minZ, m.pos().z); maxZ = Math.max(maxZ, m.pos().z);
                }
                bx = (minX + maxX) * 8 + 8;   // middle of the bounding box, in blocks
                bz = (minZ + maxZ) * 8 + 8;
                spanX = maxX - minX + 1;
                spanZ = maxZ - minZ + 1;
            }
            boolean geode = peak.budding() > 0;
            out.add(new Cluster(bx, geode ? peak.amY() : peak.avgY(), bz,
                score, members.size(), peak.heads() >= MIN_HEADS ? (int) Math.round(peak.doneFraction() * 100) : -1,
                peak.old(), peak.fresh(), peak.above() >= ABOVE_COLUMNS, peak.rough(), spanX, spanZ,
                geode ? (int) Math.round(peak.amFill() * 100) : -1, geode ? peak.cavePct() : -1));
        }
        out.sort(Comparator.comparingInt(Cluster::score).reversed());
        return out.size() > MAX_CLUSTERS ? new ArrayList<>(out.subList(0, MAX_CLUSTERS)) : out;
    }
}
