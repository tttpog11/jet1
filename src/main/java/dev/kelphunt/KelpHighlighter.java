package dev.kelphunt;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ChunkPos;
import xaero.map.highlight.ChunkHighlighter;

import java.util.List;

/** Paints scored chunks onto Xaero's World Map (and minimap overlay). Yellow = weak, red = strong. */
public class KelpHighlighter extends ChunkHighlighter {
    public KelpHighlighter() {
        super(true);
    }

    private static ChunkReport get(int chunkX, int chunkZ) {
        return KelpData.positives.get(ChunkPos.asLong(chunkX, chunkZ));
    }

    @Override
    public boolean regionHasHighlights(ResourceKey<Level> dim, int regionX, int regionZ) {
        for (ChunkReport r : KelpData.positives.values()) {
            if ((r.pos().x >> 5) == regionX && (r.pos().z >> 5) == regionZ) return true;
        }
        return false;
    }

    @Override
    public int calculateRegionHash(ResourceKey<Level> dim, int regionX, int regionZ) {
        return KelpData.version * 31 + 7;
    }

    @Override
    public boolean chunkIsHighlit(ResourceKey<Level> dim, int chunkX, int chunkZ) {
        return get(chunkX, chunkZ) != null;
    }

    private static int color(int score, int alpha) {
        float t = Math.min(1f, Math.max(0f, score / 100f));
        int g = (int) (200 - 170 * t); // yellow (200) -> red (30)
        return (alpha << 24) | (0xFF << 16) | (g << 8) | 0x20;
    }

    @Override
    protected int[] getColors(ResourceKey<Level> dim, int chunkX, int chunkZ) {
        ChunkReport r = get(chunkX, chunkZ);
        if (r == null) return null;
        int fill = color(r.score(), 0x55);
        int edge = color(r.score(), 0xB0);
        // [0] fill, [1] north edge, [2] east edge, [3] south edge, [4] west edge
        resultStore[0] = fill;
        resultStore[1] = get(chunkX, chunkZ - 1) == null ? edge : fill;
        resultStore[2] = get(chunkX + 1, chunkZ) == null ? edge : fill;
        resultStore[3] = get(chunkX, chunkZ + 1) == null ? edge : fill;
        resultStore[4] = get(chunkX - 1, chunkZ) == null ? edge : fill;
        return resultStore;
    }

    private static Component tip(int chunkX, int chunkZ) {
        ChunkReport r = get(chunkX, chunkZ);
        if (r == null) return null;
        StringBuilder sb = new StringBuilder("Kelp Hunt: ")
            .append(r.score() >= 70 ? "high" : r.score() >= 35 ? "medium" : "low")
            .append(" confidence, score ").append(r.score());
        sb.append(" (");
        if (r.heads() > 0) sb.append(String.format("%.0f%% of kelp fully grown", r.doneFraction() * 100));
        else sb.append("too little kelp to judge");
        if (r.budding() > 0) sb.append(String.format(", amethyst %.0f%% grown, caves %d%%", r.amFill() * 100, r.cavePct()));
        if (r.old()) sb.append(", old chunk"); else if (r.fresh()) sb.append(", new chunk");
        if (r.above() >= 3) sb.append(", above-water blocks");
        if (r.rough()) sb.append(", rough seabed");
        return Component.literal(sb.append(")").toString());
    }

    @Override
    public Component getChunkHighlightSubtleTooltip(ResourceKey<Level> dim, int chunkX, int chunkZ) {
        return tip(chunkX, chunkZ);
    }

    @Override
    public Component getChunkHighlightBluntTooltip(ResourceKey<Level> dim, int chunkX, int chunkZ) {
        return tip(chunkX, chunkZ);
    }

    @Override
    public void addMinimapBlockHighlightTooltips(List<Component> list, ResourceKey<Level> dim, int a, int b, int c) {
        // no minimap tooltips
    }
}
