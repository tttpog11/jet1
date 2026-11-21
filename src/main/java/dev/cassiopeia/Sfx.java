package dev.cassiopeia;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

/**
 * The strike's sounds, all vanilla. They are looked up by name instead of through SoundEvents' constants, which makes
 * no difference to the player but means this code doesn't care whether a constant is a SoundEvent or a Holder in a
 * given Minecraft version.
 */
final class Sfx {
    static final SoundEvent SIGIL = named("block.end_portal.spawn");
    static final SoundEvent LAUNCH = named("entity.firework_rocket.launch");
    static final SoundEvent THUNDER = named("entity.lightning_bolt.thunder");
    static final SoundEvent IMPACT = named("entity.lightning_bolt.impact");
    static final SoundEvent SHATTER = named("block.amethyst_cluster.break");
    static final SoundEvent CHIME = named("block.amethyst_block.chime");

    private Sfx() {
    }

    private static SoundEvent named(String path) {
        return SoundEvent.createVariableRangeEvent(Identifier.withDefaultNamespace(path));
    }

    /** Plays {@code sound} at {@code at} for everyone in range. A volume above 1 makes it carry further. */
    static void play(ServerLevel level, Vec3 at, SoundEvent sound, float volume, float pitch) {
        level.playSound(null, at.x, at.y, at.z, sound, SoundSource.PLAYERS, volume, pitch);
    }
}
