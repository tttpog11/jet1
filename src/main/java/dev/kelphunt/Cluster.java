package dev.kelphunt;

/**
 * A group of neighbouring high-scoring chunks; becomes one waypoint.
 * {@code grownPct}, {@code amethystPct} and {@code cavePct} are -1 when not measured in the strongest chunk.
 */
public record Cluster(int x, int y, int z, int score, int chunks, int grownPct,
                      boolean old, boolean fresh, boolean above, boolean rough,
                      int spanX, int spanZ, int amethystPct, int cavePct) {}
