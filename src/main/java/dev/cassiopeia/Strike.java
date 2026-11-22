package dev.cassiopeia;

import cyou.rimuru.rimulib.api.AimApi;
import cyou.rimuru.rimulib.api.SkillApi;
import cyou.rimuru.rimulib.skill.Tuning;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * One cast of Cassiopeia, on the server: it follows {@link Timeline} tick by tick (the speed slider scales the clock),
 * and when a star hits it hurts what stands under it, throws it aside, digs the crater and sets the star core. After
 * the last star it lays the starlight veins along the W. Everything the players see and hear in the sky is the client's
 * ({@code client/Show}, {@code client/Film}), started by the {@link Plan} packet sent in {@link #cast}.
 */
final class Strike {
    private static final List<Strike> ACTIVE = new ArrayList<>();
    /** Columns of crater dug per crater per tick. */
    private static final int COLUMNS_PER_TICK = 140;

    private final ServerLevel level;
    private final UUID caster;
    private final Plan plan;
    private final boolean erase;
    private final float damage;
    private final boolean[] launched = new boolean[Timeline.STARS];
    private final boolean[] hit = new boolean[Timeline.STARS];
    private final List<Crater> craters = new ArrayList<>();
    private float clock;
    private List<int[]> veins;
    private int veinsLaid;

    private Strike(ServerLevel level, UUID caster, Plan plan, Tuning tuning) {
        this.level = level;
        this.caster = caster;
        this.plan = plan;
        // the remote's Damage slider: a number, or infinity, which erases whatever stands in a crater
        this.erase = tuning.erases();
        this.damage = this.erase ? 0.0F : tuning.damage();
    }

    /** The remote calls this when the button is pressed. Returns false (after telling the player why) if it can't start. */
    static boolean cast(ServerPlayer player, Tuning tuning) {
        // (the cast is a no-op where this is already a ServerLevel, and needed where level() is typed as Level)
        ServerLevel level = (ServerLevel) player.level();
        if (tuning == null) {
            tuning = Tuning.initial(CassiopeiaSkill.INSTANCE);
        }
        if (level.dimensionType().hasCeiling()) {
            SkillApi.deny(player, Component.literal(CassiopeiaSkill.INSTANCE.title() + ": there is no sky here"));
            return false;
        }
        for (Strike strike : ACTIVE) {
            if (strike.caster.equals(player.getUUID())) {
                SkillApi.deny(player, Component.literal(CassiopeiaSkill.INSTANCE.title() + ": the stars are already rising"));
                return false;
            }
        }
        Vec3 aim = AimApi.ground(player, 160.0, 260.0);
        if (aim == null) {
            SkillApi.deny(player, Component.literal(CassiopeiaSkill.INSTANCE.title() + ": look at the ground"));
            return false;
        }
        float power = Mth.clamp(tuning.power(), 0.3F, (float) Config.maxPower());
        float speed = Mth.clamp(tuning.speed(), 0.3F, 3.0F);
        Plan plan = Plan.make(level, aim, player.getYRot(), power, speed, player.getRandom().nextLong(), player.getId());
        ACTIVE.add(new Strike(level, player.getUUID(), plan, tuning));
        // the caster gets the film; everyone within sight of the W gets the sky
        for (ServerPlayer other : level.players()) {
            boolean near = other == player || other.distanceToSqr(plan.centre()) < 192.0 * 192.0;
            if (near && ServerPlayNetworking.canSend(other, Plan.TYPE)) {
                ServerPlayNetworking.send(other, plan);
            }
        }
        Sfx.play(level, plan.centre(), Sfx.SIGIL, 4.0F, 0.6F);
        return true;
    }

    static void tickAll(MinecraftServer server) {
        Iterator<Strike> it = ACTIVE.iterator();
        while (it.hasNext()) {
            Strike strike = it.next();
            boolean done;
            try {
                done = strike.tick();
            } catch (RuntimeException failed) {
                // one broken strike must never take the server down with it
                CassiopeiaMod.LOG.error("A Cassiopeia strike failed and was dropped", failed);
                done = true;
            }
            if (done) {
                it.remove();
            }
        }
    }

    static void clear() {
        ACTIVE.clear();
    }

    /** Returns true when this strike is over. */
    private boolean tick() {
        this.clock += this.plan.speed();
        for (int k = 0; k < Timeline.STARS; k++) {
            int star = Plan.ORDER[k];
            if (!this.launched[k] && this.clock >= Timeline.launch(k)) {
                this.launched[k] = true;
                Sfx.play(this.level, this.plan.stars()[star], Sfx.LAUNCH, 6.0F, 0.5F);
            }
            if (!this.hit[k] && this.clock >= Timeline.impact(k)) {
                this.hit[k] = true;
                this.impact(star);
            }
        }
        Iterator<Crater> it = this.craters.iterator();
        while (it.hasNext()) {
            if (it.next().step(COLUMNS_PER_TICK)) {
                it.remove();
            }
        }
        if (this.clock >= Timeline.VEINS_START) {
            this.layVeins();
        }
        boolean veinsDone = this.veins != null && this.veinsLaid >= this.veins.size();
        return this.clock >= Timeline.END && this.craters.isEmpty() && veinsDone;
    }

    private void impact(int star) {
        Vec3 at = this.plan.stars()[star];
        float radius = this.plan.radius()[star];
        Sfx.play(this.level, at, Sfx.THUNDER, 8.0F, 0.7F);
        Sfx.play(this.level, at, Sfx.IMPACT, 8.0F, 0.6F);
        Sfx.play(this.level, at, Sfx.SHATTER, 5.0F, 0.5F);
        this.hurtAround(at, radius);
        if (Config.carveTerrain()) {
            this.craters.add(new Crater(this.level, at, radius, this.plan.seed() + 31L * star));
        }
    }

    /** Hurts and throws whatever stands near the impact. With Damage at infinity, whatever stands in the crater is erased. */
    private void hurtAround(Vec3 at, float radius) {
        double reach = radius * 1.7;
        ServerPlayer caster = this.level.getServer().getPlayerList().getPlayer(this.caster);
        AABB box = new AABB(at.x - reach, at.y - reach, at.z - reach, at.x + reach, at.y + reach * 1.5, at.z + reach);
        for (LivingEntity entity : this.level.getEntitiesOfClass(LivingEntity.class, box)) {
            if (entity.getUUID().equals(this.caster)) {
                continue;
            }
            if (entity instanceof Player other && (other.isCreative() || other.isSpectator())) {
                continue;
            }
            double distance = entity.position().distanceTo(at);
            if (distance > reach) {
                continue;
            }
            double falloff = 1.0 - distance / reach;
            if (this.erase && distance <= radius * 1.15) {
                entity.hurtServer(this.level, this.level.damageSources().genericKill(), Float.MAX_VALUE);
            } else {
                float amount = this.erase ? (float) Math.max(4.0, 40.0 * falloff) : (float) Math.max(1.0, this.damage * falloff);
                entity.hurtServer(this.level, this.level.damageSources().explosion(caster, caster), amount);
            }
            Vec3 away = entity.position().subtract(at).multiply(1.0, 0.0, 1.0);
            away = away.lengthSqr() < 1.0E-4 ? new Vec3(0.0, 0.0, 1.0) : away.normalize();
            double push = 1.6 * falloff;
            entity.setDeltaMovement(entity.getDeltaMovement().add(away.scale(push)).add(0.0, 0.5 * push, 0.0));
            entity.hurtMarked = true;
        }
    }

    /** The veins run between the stars along the W, one block wide, laid from Caph to Segin. */
    private void layVeins() {
        if (this.veins == null) {
            this.veins = Config.carveTerrain() ? this.veinPath() : new ArrayList<>();
        }
        double progress = Mth.clamp((this.clock - Timeline.VEINS_START) / Timeline.VEINS_LEN, 0.0F, 1.0F);
        int target = (int) Math.ceil(progress * this.veins.size());
        while (this.veinsLaid < target) {
            int[] column = this.veins.get(this.veinsLaid);
            this.placeVein(column[0], column[1]);
            if (this.veinsLaid % 10 == 0) {
                Sfx.play(this.level, new Vec3(column[0] + 0.5, this.plan.centre().y + 2.0, column[1] + 0.5), Sfx.CHIME, 3.0F,
                    0.6F + 0.9F * (float) this.veinsLaid / Math.max(1, this.veins.size()));
            }
            this.veinsLaid++;
        }
    }

    private List<int[]> veinPath() {
        Set<Long> seen = new LinkedHashSet<>();
        List<int[]> path = new ArrayList<>();
        for (int i = 0; i < Timeline.STARS - 1; i++) {
            Vec3 a = this.plan.stars()[i];
            Vec3 b = this.plan.stars()[i + 1];
            int steps = (int) Math.ceil(a.distanceTo(b) * 2.0);
            for (int s = 0; s <= steps; s++) {
                double u = steps == 0 ? 0.0 : (double) s / steps;
                int x = Mth.floor(Mth.lerp(u, a.x, b.x));
                int z = Mth.floor(Mth.lerp(u, a.z, b.z));
                if (seen.add(((long) x << 32) ^ (z & 0xFFFFFFFFL))) {
                    path.add(new int[]{x, z});
                }
            }
        }
        return path;
    }

    /** Crying obsidian on the top block of the column (it glows violet), and whatever grows on it cleared away. */
    private void placeVein(int x, int z) {
        int top = this.level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
        BlockPos pos = new BlockPos(x, top, z);
        BlockState state = this.level.getBlockState(pos);
        if (state.isAir() || !state.getFluidState().isEmpty() || state.getDestroySpeed(this.level, pos) < 0.0F
            || state.is(Blocks.SEA_LANTERN) || state.is(Blocks.AMETHYST_CLUSTER)) {
            return;
        }
        this.level.setBlock(pos, Blocks.CRYING_OBSIDIAN.defaultBlockState(), 3);
        BlockPos above = pos.above();
        BlockState over = this.level.getBlockState(above);
        if (!over.isAir() && over.canBeReplaced() && over.getFluidState().isEmpty()) {
            this.level.setBlock(above, Blocks.AIR.defaultBlockState(), 3);
        }
    }
}
