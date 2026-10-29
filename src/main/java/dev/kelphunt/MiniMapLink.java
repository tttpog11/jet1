package dev.kelphunt;

import net.minecraft.client.Minecraft;
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
    /**
     * Waypoints we create are named "Amethyst High - ..." or "Kelp Low - ...". They are found again by symbol
     * ("A" or "K") plus that name prefix, so your own waypoints are never touched. "KH " is the old prefix.
     */
    private static final String AMETHYST = "Amethyst ", KELP = "Kelp ", OLD = "KH ", GEODE = "Geode ";

    private MiniMapLink() {}

    /** Low (under 35), Confident (35-69), High (70+). */
    private static String labelFor(int score) {
        if (score >= 70) return "High";
        if (score >= 35) return "Confident";
        return "Low";
    }

    private static boolean isAmethyst(Cluster c) { return c.amethystPct() >= 0; }

    /** e.g. "Amethyst High - 92 pct grown, caves 11 pct" or "Kelp Low - 84 pct grown, old chunk". */
    private static String nameFor(Cluster c) {
        StringBuilder sb = new StringBuilder(isAmethyst(c) ? AMETHYST : KELP).append(labelFor(c.score())).append(" -");
        if (isAmethyst(c)) {
            sb.append(' ').append(c.amethystPct()).append(" pct grown");
            if (c.grownPct() >= 0) sb.append(", kelp ").append(c.grownPct()).append(" pct grown");
            sb.append(", caves ").append(c.cavePct()).append(" pct");
        } else if (c.grownPct() >= 0) {
            sb.append(' ').append(c.grownPct()).append(" pct grown");
        }
        if (c.above()) sb.append(", above-water blocks");
        if (c.rough()) sb.append(", rough seabed");
        if (c.fresh()) sb.append(", new chunk");
        else if (c.old()) sb.append(", old chunk");
        if (c.spanX() > 1 || c.spanZ() > 1) sb.append(", ").append(c.spanX()).append("x").append(c.spanZ()).append(" chunk patch");
        else if (c.chunks() > 1) sb.append(", ").append(c.chunks()).append(" chunks");
        return sb.toString();
    }

    private static WaypointColor colorFor(int score) {
        if (score >= 70) return WaypointColor.RED;
        if (score >= 35) return WaypointColor.GOLD;
        return WaypointColor.YELLOW;
    }

    /** Replaces our previous waypoints with the given clusters. Returns how many were added, or -1 if unavailable. */
    public static int apply(List<Cluster> clusters) {
        return apply(clusters, true);
    }

    private static int apply(List<Cluster> clusters, boolean withTargets) {
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
                set.add(new Waypoint(c.x(), c.y(), c.z(), nameFor(c), isAmethyst(c) ? "A" : "K", colorFor(c.score())));
                n++;
            }
            if (withTargets) n += addTargets(set, clusters);
            ms.getWorldManagerIO().saveWorld(world);
            return n;
        } catch (Throwable t) {
            KelpHuntClient.LOGGER.warn("Could not update Minimap waypoints", t);
            return -1;
        }
    }

    public static void removeAll() {
        apply(List.of(), false);
    }

    /**
     * Waypoints for seed-predicted geodes from kelphunt-geodes.csv: green once scanned with amethyst found,
     * gray if the chunk was scanned and nothing was there, light blue if it hasn't been scanned yet.
     * Only the 100 nearest are made, and ones already covered by an Amethyst waypoint are skipped.
     */
    private static int addTargets(WaypointSet set, List<Cluster> clusters) {
        List<int[]> targets = new ArrayList<>(GeodeTargets.chunks);
        if (targets.isEmpty()) return 0;
        Minecraft mc = Minecraft.getInstance();
        double px = mc.player == null ? 0 : mc.player.getX();
        double pz = mc.player == null ? 0 : mc.player.getZ();
        targets.sort(java.util.Comparator.comparingDouble(t -> Math.pow(t[0] * 16 + 8 - px, 2) + Math.pow(t[1] * 16 + 8 - pz, 2)));

        int n = 0;
        for (int[] t : targets) {
            if (n >= 100) break;
            int cx = t[0], cz = t[1];
            int bx = cx * 16 + 8, bz = cz * 16 + 8;
            boolean covered = false;
            for (Cluster c : clusters) {
                if (c.amethystPct() >= 0 && Math.abs(c.x() - bx) <= 48 && Math.abs(c.z() - bz) <= 48) { covered = true; break; }
            }
            if (covered) continue;

            boolean scanned = false;
            ChunkReport seen = null;
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (KelpHuntClient.SCANNER.isScanned(cx + dx, cz + dz)) scanned = true;
                    ChunkReport r = KelpHuntClient.SCANNER.reportAt(cx + dx, cz + dz);
                    if (r != null && r.budding() > 0 && (seen == null || r.amFill() > seen.amFill())) seen = r;
                }
            }
            if (seen != null) {
                set.add(new Waypoint(seen.amX(), seen.amY(), seen.amZ(),
                    GEODE + "seen - amethyst " + Math.round(seen.amFill() * 100) + " pct grown", "G", WaypointColor.GREEN));
            } else if (scanned) {
                set.add(new Waypoint(bx, -20, bz, GEODE + "target - nothing found when scanned", "G", WaypointColor.GRAY));
            } else {
                set.add(new Waypoint(bx, -20, bz, GEODE + "target - not scanned yet", "G", WaypointColor.LIGHT_BLUE));
            }
            n++;
        }
        return n;
    }

    private static void removeOurs(WaypointSet set) {
        List<Waypoint> mine = new ArrayList<>();
        for (Waypoint w : set.getWaypoints()) {
            String name = w.getName();
            String sym = w.getSymbol();
            boolean ours = name != null
                && (("K".equals(sym) || "A".equals(sym)) && (name.startsWith(AMETHYST) || name.startsWith(KELP) || name.startsWith(OLD))
                    || "G".equals(sym) && name.startsWith(GEODE));
            if (ours) mine.add(w);
        }
        set.removeAll(mine);
    }
}
