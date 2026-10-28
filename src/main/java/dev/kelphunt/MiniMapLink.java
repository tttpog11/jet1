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
                set.add(new Waypoint(c.x(), c.y(), c.z(), PREFIX + labelFor(c.score()), "K", colorFor(c.score())));
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
