package dev.kelphunt;

import xaero.map.WorldMapSession;
import xaero.map.highlight.HighlighterRegistry;
import xaero.map.world.MapDimension;
import xaero.map.world.MapWorld;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/** Everything that touches Xaero's World Map classes. Only loaded when that mod is present. */
public final class WorldMapLink {
    private static final KelpHighlighter HIGHLIGHTER = new KelpHighlighter();

    private WorldMapLink() {}

    /** Registers our highlighter on the current map session if it isn't there yet. */
    public static void ensureRegistered() {
        try {
            WorldMapSession session = WorldMapSession.getCurrentSession();
            if (session == null || !session.isUsable()) return;
            HighlighterRegistry registry = session.getMapProcessor().getHighlighterRegistry();
            if (registry == null || registry.getHighlighters().contains(HIGHLIGHTER)) return;
            // The registry is sealed (unmodifiable) once the map starts, so swap in a copy that includes us.
            Field f = HighlighterRegistry.class.getDeclaredField("highlighters");
            f.setAccessible(true);
            List<xaero.map.highlight.AbstractHighlighter> copy = new ArrayList<>(registry.getHighlighters());
            copy.add(HIGHLIGHTER);
            f.set(registry, java.util.Collections.unmodifiableList(copy));
            KelpHuntClient.LOGGER.info("Registered Kelp Hunt highlighter on Xaero's World Map");
        } catch (Throwable t) {
            KelpHuntClient.LOGGER.warn("Could not register World Map highlighter", t);
        }
    }

    /** Makes the map redraw highlighted regions after new results. */
    public static void refresh() {
        try {
            WorldMapSession session = WorldMapSession.getCurrentSession();
            if (session == null || !session.isUsable()) return;
            MapWorld world = session.getMapProcessor().getMapWorld();
            if (world == null) return;
            MapDimension dim = world.getCurrentDimension();
            if (dim != null) dim.getHighlightHandler().clearCachedHashes();
        } catch (Throwable t) {
            KelpHuntClient.LOGGER.warn("Could not refresh World Map highlights", t);
        }
    }
}
