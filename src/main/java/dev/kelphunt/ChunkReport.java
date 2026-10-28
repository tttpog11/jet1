package dev.kelphunt;

import net.minecraft.world.level.ChunkPos;

/**
 * Kelp statistics for one loaded ocean chunk.
 *
 * @param heads        number of kelp tops found
 * @param doneFraction fraction of those tops that can no longer grow (age 25, or capped by the surface)
 * @param avgY         mean Y of the kelp tops (used for the waypoint height)
 * @param score        0-100 final score (kelp maturity vs the ocean average, adjusted by old/new chunk status)
 * @param old          XaeroPlus lists this chunk as an old chunk
 * @param fresh        XaeroPlus lists this chunk as newly generated
 */
public record ChunkReport(ChunkPos pos, int heads, double doneFraction, int avgY, int score, boolean old, boolean fresh) {
    public ChunkReport withScore(int newScore) {
        return new ChunkReport(pos, heads, doneFraction, avgY, newScore, old, fresh);
    }
}
