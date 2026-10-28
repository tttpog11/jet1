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
                .then(ClientCommandManager.literal("why")
                    .executes(ctx -> why(ctx.getSource())))
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

    /** Explains the score of the candidate spot nearest to you. Never prints coordinates. */
    private static int why(FabricClientCommandSource src) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || KelpData.clusters.isEmpty()) {
            say(src, "no candidate spots yet. Run /kelphunt scan first.");
            return 0;
        }
        Cluster nearest = null;
        double bestD = Double.MAX_VALUE;
        for (Cluster c : KelpData.clusters) {
            double d = mc.player.distanceToSqr(c.x(), c.y(), c.z());
            if (d < bestD) { bestD = d; nearest = c; }
        }
        say(src, "nearest candidate (about " + (int) Math.sqrt(bestD) + " blocks away): " + nearest.why());
        return 1;
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
