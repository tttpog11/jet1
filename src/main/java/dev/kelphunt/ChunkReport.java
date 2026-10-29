package dev.kelphunt;

import net.minecraft.world.level.ChunkPos;

/**
 * What the scanner measured in one loaded chunk.
 *
 * @param heads        number of kelp tops found (0 if the chunk has too little kelp to judge)
 * @param doneFraction fraction of those tops that can no longer grow (age 25, or capped by the surface)
 * @param avgY         mean Y of the kelp tops, or of the seabed / chests / mobs when there is no kelp
 * @param score        0-100 final score
 * @param old          XaeroPlus lists this chunk as an old chunk
 * @param fresh        XaeroPlus lists this chunk as newly generated
 * @param above        columns where something solid sticks up above the water surface
 * @param rough        seabed has an unusually high number of abrupt height steps
 * @param budding      budding amethyst blocks found (0 = no geode measured)
 * @param amFill       0-1, how fully grown the amethyst buds around them are (clusters = 1)
 * @param amY          mean Y of the budding amethyst, a rough depth
 * @param amX          mean X of the budding amethyst
 * @param amZ          mean Z of the budding amethyst
 * @param cavePct      percent of sampled underground space that is open air
 * @param mobs         mobs (not players) your client has seen in this chunk
 * @param traders      wandering traders seen
 * @param llamas       trader llamas seen
 * @param skelHorses   skeleton horses seen
 * @param chests       chests, barrels and shulker boxes in this chunk (0 unless unusually many)
 */
public record ChunkReport(ChunkPos pos, int heads, double doneFraction, int avgY, int score,
                          boolean old, boolean fresh, int above, boolean rough,
                          int budding, double amFill, int amY, int amX, int amZ, int cavePct,
                          int mobs, int traders, int llamas, int skelHorses, int chests) {
    public ChunkReport withScore(int newScore) {
        return new ChunkReport(pos, heads, doneFraction, avgY, newScore, old, fresh, above, rough,
            budding, amFill, amY, amX, amZ, cavePct, mobs, traders, llamas, skelHorses, chests);
    }

    public ChunkReport withMobs(int mobs, int traders, int llamas, int skelHorses) {
        return new ChunkReport(pos, heads, doneFraction, avgY, score, old, fresh, above, rough,
            budding, amFill, amY, amX, amZ, cavePct, mobs, traders, llamas, skelHorses, chests);
    }
}
