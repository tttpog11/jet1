package dev.cassiopeia.client;

import dev.cassiopeia.Plan;
import dev.cassiopeia.Timeline;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/**
 * What a strike looks like in the sky, drawn from the server's {@link Plan} with vanilla particles: the W traced on the
 * ground in light, each star streaking down on its slant, the blast and the light pillar where it lands, the cores
 * glowing in their craters, and the veins of light running between them. The blocks are the server's; this is only light.
 *
 * <p>It runs on its own clock, advanced by the plan's speed every client tick, on the same {@link Timeline} the server
 * uses, so the two agree to within the network's delay.
 */
@Environment(EnvType.CLIENT)
final class Show {
    private static Show current;

    private final Plan plan;
    private final RandomSource random = RandomSource.create();
    private float age;

    private Show(Plan plan) {
        this.plan = plan;
    }

    static void start(Plan plan, Minecraft client) {
        current = new Show(plan);
        if (client.player != null && client.player.getId() == plan.casterId()) {
            Film.play(plan, client);
        }
    }

    static void stop() {
        current = null;
    }

    static void tick(Minecraft client) {
        Show show = current;
        if (show == null) {
            return;
        }
        ClientLevel level = client.level;
        if (level == null) {
            current = null;
            return;
        }
        if (client.isPaused()) {
            return;
        }
        show.advance(level);
        if (show.age > Timeline.END + 20) {
            current = null;
        }
    }

    private void advance(ClientLevel level) {
        float before = this.age;
        this.age += this.plan.speed();
        this.sigil(level);
        for (int k = 0; k < Timeline.STARS; k++) {
            int star = Plan.ORDER[k];
            if (this.age >= Timeline.launch(k) && this.age < Timeline.impact(k)) {
                this.falling(level, star, (this.age - Timeline.launch(k)) / Timeline.FALL);
            }
            if (before < Timeline.impact(k) && this.age >= Timeline.impact(k)) {
                this.impact(level, star);
            }
            if (this.age >= Timeline.impact(k) && this.age < Timeline.END) {
                this.glow(level, star);
            }
        }
        if (this.age >= Timeline.VEINS_START && this.age < Timeline.VEINS_START + Timeline.VEINS_LEN + 20) {
            this.veins(level);
        }
    }

    /** The W traced on the ground in light, first quickly, then left to shimmer until the veins take over. */
    private void sigil(ClientLevel level) {
        if (this.age >= Timeline.VEINS_START) {
            return;
        }
        double total = this.plan.pathLength();
        double drawn = total * Mth.clamp(this.age / Timeline.SIGIL_END, 0.0F, 1.0F);
        int count = this.age < Timeline.SIGIL_END ? 14 : 3;
        for (int i = 0; i < count; i++) {
            Vec3 p = this.plan.along(this.random.nextDouble() * drawn);
            spawn(level, ParticleTypes.END_ROD, p.x, p.y + 0.3 + this.random.nextDouble() * 0.6, p.z, 0.0, 0.02, 0.0);
        }
        if (this.age < Timeline.SIGIL_END) {
            Vec3 head = this.plan.along(drawn);
            for (int i = 0; i < 5; i++) {
                spawn(level, ParticleTypes.ELECTRIC_SPARK, head.x + this.gauss(0.3), head.y + 0.6 + this.gauss(0.3),
                    head.z + this.gauss(0.3), this.gauss(0.05), 0.1, this.gauss(0.05));
            }
        }
        // a short column of light over every star
        for (Vec3 star : this.plan.stars()) {
            spawn(level, ParticleTypes.END_ROD, star.x + this.gauss(0.2), star.y + this.random.nextDouble() * 3.0, star.z + this.gauss(0.2),
                0.0, 0.05, 0.0);
        }
    }

    /** The star on its way down: a bright head and a long white-gold trail. Always visible, so the whole fall can be seen. */
    private void falling(ClientLevel level, int star, double s) {
        Vec3 head = this.plan.head(star, s);
        for (int i = 0; i < 4; i++) {
            spawn(level, ParticleTypes.END_ROD, head.x + this.gauss(0.35), head.y + this.gauss(0.35), head.z + this.gauss(0.35), 0.0, 0.0, 0.0);
        }
        for (int i = 0; i < 2; i++) {
            spawn(level, ParticleTypes.FLAME, head.x + this.gauss(0.4), head.y + this.gauss(0.4), head.z + this.gauss(0.4), 0.0, 0.0, 0.0);
        }
        spawn(level, ParticleTypes.LARGE_SMOKE, head.x, head.y, head.z, 0.0, 0.0, 0.0);
        for (int i = 1; i <= 14; i++) {
            Vec3 behind = this.plan.head(star, Math.max(0.0, s - i * 0.012));
            spawn(level, ParticleTypes.END_ROD, behind.x + this.gauss(0.4), behind.y + this.gauss(0.4), behind.z + this.gauss(0.4), 0.0, 0.0, 0.0);
            if (i % 3 == 0) {
                spawn(level, ParticleTypes.FLAME, behind.x + this.gauss(0.5), behind.y + this.gauss(0.5), behind.z + this.gauss(0.5), 0.0, 0.0, 0.0);
            }
        }
    }

    /** The blast: a fireball, a shock ring, a pillar of light and a dome of sparks, all scaled by the crater's size. */
    private void impact(ClientLevel level, int star) {
        Vec3 at = this.plan.stars()[star];
        double radius = this.plan.radius()[star];
        spawn(level, ParticleTypes.EXPLOSION_EMITTER, at.x, at.y + 1.0, at.z, 0.0, 0.0, 0.0);
        int ring = 56;
        for (int i = 0; i < ring; i++) {
            double angle = Math.PI * 2.0 * i / ring;
            double speed = 0.9 + radius * 0.06;
            spawn(level, ParticleTypes.END_ROD, at.x + Math.cos(angle), at.y + 0.5, at.z + Math.sin(angle),
                Math.cos(angle) * speed, 0.06, Math.sin(angle) * speed);
            spawn(level, ParticleTypes.CLOUD, at.x + Math.cos(angle), at.y + 0.5, at.z + Math.sin(angle),
                Math.cos(angle) * speed * 0.7, 0.1, Math.sin(angle) * speed * 0.7);
        }
        for (int i = 0; i < 60; i++) {
            spawn(level, ParticleTypes.END_ROD, at.x + this.gauss(0.6), at.y + 1.0, at.z + this.gauss(0.6), 0.0, 0.9 + this.random.nextDouble() * 1.6, 0.0);
        }
        for (int i = 0; i < 40; i++) {
            double angle = this.random.nextDouble() * Math.PI * 2.0;
            double lift = 0.3 + this.random.nextDouble();
            double speed = (0.4 + this.random.nextDouble() * 0.9) * (0.5 + radius * 0.05);
            spawn(level, ParticleTypes.ELECTRIC_SPARK, at.x, at.y + 1.0, at.z, Math.cos(angle) * speed, lift, Math.sin(angle) * speed);
        }
        for (int i = 0; i < 24; i++) {
            spawn(level, ParticleTypes.LARGE_SMOKE, at.x + this.gauss(radius * 0.3), at.y + 1.0, at.z + this.gauss(radius * 0.3), 0.0, 0.15 + this.random.nextDouble() * 0.2, 0.0);
        }
    }

    /** The star core burning in its crater until the film ends. */
    private void glow(ClientLevel level, int star) {
        Vec3 at = this.plan.stars()[star];
        double floor = at.y - 1.0 - Math.round(this.plan.radius()[star] * 0.55);
        for (int i = 0; i < 2; i++) {
            spawn(level, ParticleTypes.END_ROD, at.x + 0.5 + this.gauss(0.5), floor + 2.0 + this.random.nextDouble() * 2.0, at.z + 0.5 + this.gauss(0.5),
                0.0, 0.06 + this.random.nextDouble() * 0.1, 0.0);
        }
    }

    /** Sparks run along the W as the server lays the veins, head first. */
    private void veins(ClientLevel level) {
        double total = this.plan.pathLength();
        double progress = Mth.clamp((this.age - Timeline.VEINS_START) / Timeline.VEINS_LEN, 0.0F, 1.0F);
        Vec3 head = this.plan.along(total * progress);
        for (int i = 0; i < 8; i++) {
            spawn(level, ParticleTypes.ELECTRIC_SPARK, head.x + this.gauss(0.4), head.y + 0.4 + this.random.nextDouble(), head.z + this.gauss(0.4),
                this.gauss(0.1), 0.15, this.gauss(0.1));
        }
        for (int i = 0; i < 6; i++) {
            Vec3 p = this.plan.along(this.random.nextDouble() * total * progress);
            spawn(level, ParticleTypes.END_ROD, p.x, p.y + 0.3, p.z, 0.0, 0.04, 0.0);
        }
    }

    private double gauss(double spread) {
        return this.random.nextGaussian() * spread;
    }

    /**
     * Every particle goes through here. "Always visible" particles are not culled at 32 blocks from the camera, which
     * is what lets a star be seen falling from 170 blocks up. If your Minecraft version has no
     * {@code addAlwaysVisibleParticle}, change this one line to {@code level.addParticle(...)} (the stars then only
     * show on their last 32 blocks).
     */
    private static void spawn(ClientLevel level, ParticleOptions particle, double x, double y, double z, double vx, double vy, double vz) {
        level.addAlwaysVisibleParticle(particle, x, y, z, vx, vy, vz);
    }
}
