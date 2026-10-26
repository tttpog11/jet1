package com.tttpog.stormfront.client;

import com.tttpog.stormfront.Config;
import com.tttpog.stormfront.storm.StormData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;

/** Client copy of the storms + tornado sound, debris particles and camera shake. */
@Mod.EventBusSubscriber(modid = "stormfront", bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class ClientStorms {
    private static final List<StormData> STORMS = new ArrayList<>();
    private static long clientTicks = 0;
    private static long receivedAt = 0;

    /** Kept at 0 for now (rain comes in a later step); the cloud renderer reads it. */
    public static float localRain = 0f;

    private ClientStorms() {}

    public static void receive(List<StormData> list) {
        STORMS.clear();
        STORMS.addAll(list);
        receivedAt = clientTicks;
    }

    /** Storms extrapolated to "now". */
    public static List<StormData> current(float partial) {
        List<StormData> out = new ArrayList<>(STORMS.size());
        double dt = (clientTicks + partial) - receivedAt;
        for (StormData s : STORMS) out.add(s.copyAdvanced(dt));
        return out;
    }

    public static boolean hasStorms() {
        return !STORMS.isEmpty();
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.isPaused()) return;
        clientTicks++;

        if (mc.level.dimension() != Level.OVERWORLD) STORMS.clear();
        if (STORMS.isEmpty()) return;

        LocalPlayer pl = mc.player;
        double px = pl.getX(), pz = pl.getZ();
        RandomSource rnd = mc.level.random;
        List<StormData> now = current(0f);

        StormData tor = nearestTornado(now, px, pz);
        if (tor == null) return;

        double dx = tor.torX - px, dz = tor.torZ - pz;
        double dist = Math.sqrt(dx * dx + dz * dz);
        float vol = (float) Mth.clamp(1.0 - dist / 1300.0, 0.0, 1.0) * tor.torInt;
        if (vol > 0.03f && clientTicks % 6 == 0) {
            mc.level.playLocalSound(px, pl.getY(), pz, SoundEvents.ENDER_DRAGON_FLAP, SoundSource.WEATHER,
                    0.8f * vol * vol + 0.05f, 0.45f + 0.1f * rnd.nextFloat(), false);
            mc.level.playLocalSound(px, pl.getY(), pz, SoundEvents.WEATHER_RAIN_ABOVE, SoundSource.WEATHER,
                    1.5f * vol, 0.5f, false);
        }
        if (dist < 170 && tor.torInt > 0.25f) {
            float r = tor.torRadiusNow();
            int n = (int) (6 + r * 0.25);
            for (int i = 0; i < n; i++) {
                double a = rnd.nextDouble() * Math.PI * 2;
                double d = r * (0.3 + rnd.nextDouble() * 0.9);
                double x = tor.torX + Math.cos(a) * d;
                double z = tor.torZ + Math.sin(a) * d;
                double y = tor.torY + rnd.nextDouble() * r * 1.2;
                double sp = 0.5 + 0.3 * rnd.nextDouble();
                mc.level.addAlwaysVisibleParticle(rnd.nextInt(3) == 0 ? ParticleTypes.POOF : ParticleTypes.LARGE_SMOKE,
                        x, y, z, -Math.sin(a) * sp, 0.25 + rnd.nextDouble() * 0.4, Math.cos(a) * sp);
            }
        }
    }

    public static StormData nearestTornado(List<StormData> list, double px, double pz) {
        StormData best = null;
        double bd = Double.MAX_VALUE;
        for (StormData s : list) {
            if (!s.tornado || s.torInt < 0.05f) continue;
            double d = (s.torX - px) * (s.torX - px) + (s.torZ - pz) * (s.torZ - pz);
            if (d < bd) {
                bd = d;
                best = s;
            }
        }
        return best;
    }

    @SubscribeEvent
    public static void onCameraAngles(ViewportEvent.ComputeCameraAngles e) {
        if (!Config.CAMERA_SHAKE.get() || STORMS.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        StormData tor = nearestTornado(current(0f), mc.player.getX(), mc.player.getZ());
        if (tor == null) return;
        double dx = tor.torX - mc.player.getX(), dz = tor.torZ - mc.player.getZ();
        double dist = Math.sqrt(dx * dx + dz * dz);
        double reach = tor.torRadiusNow() * 5 + 70;
        if (dist > reach) return;
        double a = Math.pow(1.0 - dist / reach, 2) * tor.torInt * (1.0 + 0.3 * tor.torEf);
        float t = (clientTicks + (float) e.getPartialTick()) * 0.9f;
        e.setRoll(e.getRoll() + (float) (Mth.sin(t * 1.7f) * a * 1.4));
        e.setPitch(e.getPitch() + (float) (Mth.sin(t * 2.3f + 1f) * a * 0.9));
        e.setYaw(e.getYaw() + (float) (Mth.sin(t * 1.9f + 2f) * a * 0.9));
    }
}
