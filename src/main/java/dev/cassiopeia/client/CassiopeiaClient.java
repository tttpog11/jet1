package dev.cassiopeia.client;

import cyou.rimuru.rimulib.client.api.FilmApi;
import dev.cassiopeia.CassiopeiaMod;
import dev.cassiopeia.Plan;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/** Client side: when the server sends a {@link Plan}, draw the sky for it and, for the caster, play the film. */
@Environment(EnvType.CLIENT)
public final class CassiopeiaClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(Plan.TYPE, (plan, context) -> Show.start(plan, context.client()));
        ClientTickEvents.END_CLIENT_TICK.register(Show::tick);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> Show.stop());
        try {
            // the remote's film key (skip) works for addon films too
            FilmApi.enableKey();
        } catch (RuntimeException failed) {
            CassiopeiaMod.LOG.warn("Couldn't enable the film key: {}", failed.toString());
        }
    }
}
