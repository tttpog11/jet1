package dev.kelphunt;

/**
 * A group of neighbouring high-scoring chunks; becomes one waypoint.
 * {@code grownPct} is -1 when the strongest chunk had too little kelp to judge.
 */
public record Cluster(int x, int y, int z, int score, int chunks, int grownPct,
                      boolean old, boolean fresh, boolean above, boolean rough,
                      int spanX, int spanZ) {}
