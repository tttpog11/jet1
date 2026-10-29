package dev.kelphunt;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class KelpHuntClient implements ClientModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("kelphunt");
    public static final KelpScanner SCANNER = new KelpScanner();

    private static boolean hasMiniMap;
    private static boolean hasWorldMap;
    private static boolean auto;
    private static int autoRadius = 12;
    private static int tickCounter;

    @Override
    public void onInitializeClient() {
        FabricLoader loader = FabricLoader.getInstance();
        hasMiniMap = loader.isModLoaded("xaerominimap");
        hasWorldMap = loader.isModLoaded("xaeroworldmap");

        ClientTickEvents.END_CLIENT_TICK.register(client -> tick());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            auto = false;
            SCANNER.clearAll();
        });

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
            dispatcher.register(ClientCommandManager.literal("kelphunt")
                .then(ClientCommandManager.literal("scan")
                    .executes(ctx -> scan(ctx.getSource(), 12))
                    .then(ClientCommandManager.argument("radius", IntegerArgumentType.integer(2, 32))
                        .executes(ctx -> scan(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "radius")))))
                .then(ClientCommandManager.literal("auto")
                    .executes(ctx -> toggleAuto(ctx.getSource())))
                .then(ClientCommandManager.literal("min")
                    .then(ClientCommandManager.argument("score", IntegerArgumentType.integer(1, 100))
                        .executes(ctx -> {
                            SCANNER.minWaypointScore = IntegerArgumentType.getInteger(ctx, "score");
                            say(ctx.getSource(), "waypoints will be made for scores " + SCANNER.minWaypointScore + "+ (applies on the next scan)");
                            return 1;
                        })))
                .then(ClientCommandManager.literal("skipnear")
                    .executes(ctx -> {
                        SCANNER.skipNear = !SCANNER.skipNear;
                        say(ctx.getSource(), SCANNER.skipNear
                            ? "skipping chunks inside your own simulation distance (you load those yourself). Applies on the next scan."
                            : "scoring every loaded chunk, including ones you are loading yourself.");
                        return 1;
                    }))
                .then(ClientCommandManager.literal("mobs")
                    .executes(ctx -> {
                        SCANNER.setMobs(!SCANNER.mobs);
                        say(ctx.getSource(), SCANNER.mobs
                            ? "mob evidence ON: wandering traders, trader llamas, skeleton horses and crowds your client can currently see. Counted when a scan finishes."
                            : "mob evidence off.");
                        return 1;
                    }))
                .then(ClientCommandManager.literal("geodes")
                    .then(ClientCommandManager.literal("load")
                        .executes(ctx -> loadGeodes(ctx.getSource(), false))
                        .then(ClientCommandManager.literal("block")
                            .executes(ctx -> loadGeodes(ctx.getSource(), true))))
                    .then(ClientCommandManager.literal("clear")
                        .executes(ctx -> {
                            GeodeTargets.clear();
                            if (hasMiniMap) MiniMapLink.apply(KelpData.clusters);
                            say(ctx.getSource(), "geode targets cleared.");
                            return 1;
                        })))
                .then(ClientCommandManager.literal("amethyst")
                    .executes(ctx -> {
                        SCANNER.amethyst = !SCANNER.amethyst;
                        say(ctx.getSource(), SCANNER.amethyst
                            ? "amethyst growth and cave share now count as evidence. Applies on the next scan."
                            : "amethyst scanning off. Applies on the next scan.");
                        return 1;
                    }))
                .then(ClientCommandManager.literal("terrain")
                    .executes(ctx -> {
                        SCANNER.terrain = !SCANNER.terrain;
                        say(ctx.getSource(), SCANNER.terrain
                            ? "seabed and above-water shape now count as extra evidence. Applies on the next scan."
                            : "kelp growth only. Applies on the next scan.");
                        return 1;
                    }))
                .then(ClientCommandManager.literal("status")
                    .executes(ctx -> status(ctx.getSource())))
                .then(ClientCommandManager.literal("export")
                    .executes(ctx -> export(ctx.getSource())))
                .then(ClientCommandManager.literal("clear")
                    .executes(ctx -> {
                        SCANNER.clearAll();
                        if (hasMiniMap) MiniMapLink.removeAll();
                        if (hasWorldMap) WorldMapLink.refresh();
                        say(ctx.getSource(), "cleared results and removed Kelp Hunt waypoints.");
                        return 1;
                    }))));
    }

    private static void say(FabricClientCommandSource src, String msg) {
        src.sendFeedback(Component.literal("Kelp Hunt: " + msg));
    }

    private static int scan(FabricClientCommandSource src, int radius) {
        SCANNER.start(radius);
        say(src, "scanning " + radius + " chunks out. Only chunks your client has already loaded are read. "
            + "Results appear on the World Map and as waypoints.");
        if (!hasMiniMap && !hasWorldMap) say(src, "Xaero's Minimap/World Map not found, so there is nothing to draw on.");
        return 1;
    }

    /** Reads kelphunt-geodes.csv (positions predicted from the seed by an outside tool) and marks them on the map. */
    private static int loadGeodes(FabricClientCommandSource src, boolean blockCoords) {
        try {
            int n = GeodeTargets.load(blockCoords);
            if (hasMiniMap) MiniMapLink.apply(KelpData.clusters);
            say(src, "loaded " + n + " geode target(s) from " + GeodeTargets.FILE_NAME + " (read as "
                + (blockCoords ? "block" : "chunk") + " coordinates). The 100 nearest get waypoints. Scan to update them.");
            return 1;
        } catch (java.io.IOException e) {
            say(src, "could not read " + GeodeTargets.FILE_NAME + " in your game folder.");
            return 0;
        }
    }

    private static int status(FabricClientCommandSource src) {
        FabricLoader l = FabricLoader.getInstance();
        say(src, "Minimap " + (hasMiniMap ? "yes" : "no") + ", World Map " + (hasWorldMap ? "yes" : "no")
            + ", XaeroPlus " + (l.isModLoaded("xaeroplus") ? "yes" : "no") + ". "
            + SCANNER.measuredChunks() + " ocean chunks measured, " + KelpData.clusters.size() + " candidate spot(s), "
            + KelpData.positives.size() + " highlighted chunk(s). Waypoint cutoff " + SCANNER.minWaypointScore
            + ", mobs " + (SCANNER.mobs ? "on" : "off") + ", geode targets " + GeodeTargets.chunks.size() + ", amethyst " + (SCANNER.amethyst ? "on" : "off") + ", terrain " + (SCANNER.terrain ? "on" : "off") + ", skip-near " + (SCANNER.skipNear ? "on (" + SCANNER.skippedNear() + " skipped last scan)" : "off")
            + ", auto " + (auto ? "on" : "off") + ".");
        return 1;
    }

    /** Writes candidates, with coordinates, to a file instead of chat. */
    private static int export(FabricClientCommandSource src) {
        if (KelpData.clusters.isEmpty()) {
            say(src, "nothing to export yet. Run /kelphunt scan first.");
            return 0;
        }
        try {
            java.nio.file.Path file = FabricLoader.getInstance().getGameDir().resolve("kelphunt-candidates.csv");
            StringBuilder sb = new StringBuilder("score,x,y,z,chunks,grown_pct,old_chunk,new_chunk,above_water,rough_seabed,patch_x,patch_z,amethyst_pct,cave_pct,mobs,traders,trader_llamas,skeleton_horses\n");
            for (Cluster c : KelpData.clusters) {
                sb.append(c.score()).append(',').append(c.x()).append(',').append(c.y()).append(',').append(c.z()).append(',')
                  .append(c.chunks()).append(',').append(c.grownPct()).append(',').append(c.old()).append(',').append(c.fresh()).append(',').append(c.above()).append(',').append(c.rough()).append(',').append(c.spanX()).append(',').append(c.spanZ()).append(',').append(c.amethystPct()).append(',').append(c.cavePct()).append(',').append(c.mobs()).append(',').append(c.traders()).append(',')
                  .append(c.llamas()).append(',').append(c.skelHorses()).append('\n');
            }
            java.nio.file.Files.writeString(file, sb.toString());
            say(src, "exported " + KelpData.clusters.size() + " spot(s) to kelphunt-candidates.csv in your game folder.");
            return 1;
        } catch (Exception e) {
            LOGGER.warn("Export failed", e);
            say(src, "export failed, see the game log.");
            return 0;
        }
    }

    private static int toggleAuto(FabricClientCommandSource src) {
        auto = !auto;
        say(src, auto ? "auto-scan ON (rescans every ~15s as you explore)." : "auto-scan OFF.");
        return 1;
    }

    private static void tick() {
        SCANNER.tick();
        tickCounter++;

        if (hasWorldMap && tickCounter % 40 == 0) WorldMapLink.ensureRegistered();

        if (auto && tickCounter % 300 == 0 && !SCANNER.isRunning()) SCANNER.start(autoRadius);

        if (SCANNER.pollFinished()) {
            if (hasWorldMap) WorldMapLink.refresh();
            Minecraft mc = Minecraft.getInstance();
            String msg;
            if (KelpData.clusters.isEmpty()) {
                msg = "scan done (" + SCANNER.measuredChunks() + " ocean chunks measured). Nothing stands out yet.";
                if (hasMiniMap) MiniMapLink.apply(KelpData.clusters);
            } else {
                int n = hasMiniMap ? MiniMapLink.apply(KelpData.clusters) : -1;
                int best = KelpData.clusters.get(0).score();
                msg = "scan done (" + SCANNER.measuredChunks() + " ocean chunks measured). "
                    + KelpData.clusters.size() + " candidate spot(s), best score " + best
                    + (n >= 0 ? ", waypoints added to Xaero's Minimap." : ".");
            }
            if (mc.player != null && !auto) mc.player.displayClientMessage(Component.literal("Kelp Hunt: " + msg), false);
        }
    }
}
