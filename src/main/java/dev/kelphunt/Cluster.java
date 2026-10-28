package dev.kelphunt;

/** A group of neighbouring high-scoring chunks; becomes one waypoint. */
public record Cluster(int x, int y, int z, int score, int chunks) {}
