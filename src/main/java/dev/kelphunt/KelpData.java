package dev.kelphunt;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Shared results. Read by the World Map thread, written by the client thread. */
public final class KelpData {
    /** ChunkPos.toLong() -> report, only chunks with score &gt; 0. */
    public static volatile Map<Long, ChunkReport> positives = Map.of();
    public static volatile List<Cluster> clusters = List.of();
    public static volatile int version = 0;

    private KelpData() {}

    public static void publish(Map<Long, ChunkReport> newPositives, List<Cluster> newClusters) {
        positives = new ConcurrentHashMap<>(newPositives);
        clusters = List.copyOf(newClusters);
        version++;
    }

    public static void clear() {
        publish(Map.of(), List.of());
    }
}
