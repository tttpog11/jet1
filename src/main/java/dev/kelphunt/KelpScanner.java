package dev.kelphunt;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;

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
    private static final int MAX_CLUSTERS = 20;
    private static final boolean HAS_XAEROPLUS = FabricLoader.getInstance().isModLoaded("xaeroplus");
    /** Added to the kelp score for a chunk XaeroPlus lists as old. */
    private static final int OLD_CHUNK_BONUS = 20;
    /** A newly generated chunk can't have been loaded for long, so its score is scaled down. */
    private static final double NEW_CHUNK_FACTOR = 0.25;

    /** Waypoints are only made for clusters scoring at least this. */
    public int minWaypointScore = 35;

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
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        int top = level.getSeaLevel();
        p.set(pos.getMiddleBlockX(), top - 8, pos.getMiddleBlockZ());
        if (!level.getBiome(p).is(BiomeTags.IS_OCEAN)) return null;

        int bottom = Math.max(level.getMinY(), top - 45);
        int heads = 0, done = 0;
        long ySum = 0;

        for (int x = 0; x < 16; x++) {
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
        if (heads < MIN_HEADS) return null;
        boolean old = false, fresh = false;
        if (HAS_XAEROPLUS) {
            old = XaeroPlusLink.isOld(pos.x, pos.z, level.dimension());
            fresh = XaeroPlusLink.isNew(pos.x, pos.z, level.dimension());
        }
        return new ChunkReport(pos, heads, (double) done / heads, (int) (ySum / heads), 0, old, fresh);
    }

    /**
     * Score = how many standard deviations a chunk's fully-grown fraction sits above the
     * ocean-wide average, scaled 0-100. At or below average scores 0.
     */
    private void finish() {
        finishedFlag = true;
        if (reports.size() < 5) {
            KelpData.clear();
            return;
        }
        double mean = reports.values().stream().mapToDouble(ChunkReport::doneFraction).average().orElse(0);
        double var = reports.values().stream().mapToDouble(r -> Math.pow(r.doneFraction() - mean, 2)).average().orElse(0);
        double sd = Math.max(Math.sqrt(var), 0.05); // floor stops a uniform ocean from exploding

        Map<Long, ChunkReport> positives = new HashMap<>();
        for (Map.Entry<ChunkPos, ChunkReport> e : reports.entrySet()) {
            double z = (e.getValue().doneFraction() - mean) / sd;
            int score = (int) Math.round(Math.max(0, Math.min(100, z * 40)));
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
            out.add(new Cluster(peak.pos().getMiddleBlockX(), peak.avgY(), peak.pos().getMiddleBlockZ(), score, members.size()));
        }
        out.sort(Comparator.comparingInt(Cluster::score).reversed());
        return out.size() > MAX_CLUSTERS ? new ArrayList<>(out.subList(0, MAX_CLUSTERS)) : out;
    }
}
