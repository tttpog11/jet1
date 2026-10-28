package dev.kelphunt;

import net.minecraft.world.level.ChunkPos;

/**
 * What the scanner measured in one loaded ocean chunk.
 *
 * @param heads        number of kelp tops found (0 if the chunk has too little kelp to judge)
 * @param doneFraction fraction of those tops that can no longer grow (age 25, or capped by the surface)
 * @param avgY         mean Y of the kelp tops, or of the seabed when there is no kelp (waypoint height)
 * @param score        0-100 final score
 * @param old          XaeroPlus lists this chunk as an old chunk
 * @param fresh        XaeroPlus lists this chunk as newly generated
 * @param above        columns where something solid sticks up above the water surface
 * @param rough        seabed has an unusually high number of abrupt height steps
 */
public record ChunkReport(ChunkPos pos, int heads, double doneFraction, int avgY, int score,
                          boolean old, boolean fresh, int above, boolean rough) {
    public ChunkReport withScore(int newScore) {
        return new ChunkReport(pos, heads, doneFraction, avgY, newScore, old, fresh, above, rough);
    }
}
