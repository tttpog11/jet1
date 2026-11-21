package dev.cassiopeia;

import cyou.rimuru.rimulib.api.RimulibApi;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Cassiopeia, an addon for The Shooting Star [Demo]. It only uses RimuLib's public API (SkillApi, AimApi, FilmApi):
 * none of The Shooting Star's own classes are touched, so it does not depend on that mod's version, only on RimuLib's.
 */
public final class CassiopeiaMod implements ModInitializer {
    public static final String MOD_ID = "shooting_star_cassiopeia";
    public static final Logger LOG = LoggerFactory.getLogger("Cassiopeia");

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }

    @Override
    public void onInitialize() {
        Config.load();
        RimulibApi.connect(MOD_ID, CassiopeiaMod.class, "1.3.0");
        // the strike's plan, sent to the players near it so their game can draw the sky and play the film
        PayloadTypeRegistry.playS2C().register(Plan.TYPE, Plan.CODEC);
        // joins the Stellar Remote once The Shooting Star has registered it (in either order of loading)
        CassiopeiaSkill.install();
        ServerTickEvents.END_SERVER_TICK.register(Strike::tickAll);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> Strike.clear());
    }
}
