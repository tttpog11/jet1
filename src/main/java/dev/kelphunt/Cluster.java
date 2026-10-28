package dev.kelphunt;

/** A group of neighbouring high-scoring chunks; becomes one waypoint. {@code why} explains the score. */
public record Cluster(int x, int y, int z, int score, int chunks, String why) {}
