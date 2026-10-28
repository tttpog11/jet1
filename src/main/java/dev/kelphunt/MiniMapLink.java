package dev.kelphunt;

import xaero.common.XaeroMinimapSession;
import xaero.common.minimap.waypoints.Waypoint;
import xaero.hud.minimap.BuiltInHudModules;
import xaero.hud.minimap.module.MinimapSession;
import xaero.hud.minimap.waypoint.WaypointColor;
import xaero.hud.minimap.waypoint.set.WaypointSet;
import xaero.hud.minimap.world.MinimapWorld;

import java.util.ArrayList;
import java.util.List;

/** Everything that touches Xaero's Minimap classes. Only loaded when that mod is present. */
public final class MiniMapLink {
    /** Waypoints we create are named "KH Low", "KH Confident" or "KH High"; that prefix is how we find them again. */
    private static final String PREFIX = "KH ";

    private MiniMapLink() {}

    /** Low (under 35), Confident (35-69), High (70+). */
    private static String labelFor(int score) {
        if (score >= 70) return "High";
        if (score >= 35) return "Confident";
        return "Low";
    }

    /** e.g. "KH Low - 84 pct grown, old chunk, 3 chunks": the label plus the reasons behind the score. */
    private static String nameFor(Cluster c) {
        StringBuilder sb = new StringBuilder(PREFIX).append(labelFor(c.score())).append(" -");
        if (c.grownPct() >= 0) sb.append(' ').append(c.grownPct()).append(" pct grown");
        if (c.above()) sb.append(", above-water blocks");
        if (c.rough()) sb.append(", rough seabed");
        if (c.fresh()) sb.append(", new chunk");
        else if (c.old()) sb.append(", old chunk");
        if (c.chunks() > 1) sb.append(", ").append(c.chunks()).append(" chunks");
        return sb.toString();
    }

    private static WaypointColor colorFor(int score) {
        if (score >= 70) return WaypointColor.RED;
        if (score >= 35) return WaypointColor.GOLD;
        return WaypointColor.YELLOW;
    }

    /** Replaces our previous waypoints with the given clusters. Returns how many were added, or -1 if unavailable. */
    public static int apply(List<Cluster> clusters) {
        try {
            XaeroMinimapSession hud = XaeroMinimapSession.getCurrentSession();
            if (hud == null) return -1;
            MinimapSession ms = hud.getSession(BuiltInHudModules.MINIMAP);
            if (ms == null) return -1;
            MinimapWorld world = ms.getWorldManager().getCurrentWorld();
            if (world == null) return -1;
            WaypointSet set = world.getCurrentWaypointSet();
            if (set == null) return -1;

            removeOurs(set);
            int n = 0;
            for (Cluster c : clusters) {
                set.add(new Waypoint(c.x(), c.y(), c.z(), nameFor(c), "K", colorFor(c.score())));
                n++;
            }
            ms.getWorldManagerIO().saveWorld(world);
            return n;
        } catch (Throwable t) {
            KelpHuntClient.LOGGER.warn("Could not update Minimap waypoints", t);
            return -1;
        }
    }

    public static void removeAll() {
        apply(List.of());
    }

    private static void removeOurs(WaypointSet set) {
        List<Waypoint> mine = new ArrayList<>();
        for (Waypoint w : set.getWaypoints()) {
            if (w.getName() != null && w.getName().startsWith(PREFIX)) mine.add(w);
        }
        set.removeAll(mine);
    }
}
