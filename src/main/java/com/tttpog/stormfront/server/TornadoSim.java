package com.tttpog.stormfront.server;

import com.tttpog.stormfront.Config;
import com.tttpog.stormfront.storm.StormData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Tornado destruction. Heavily budgeted: it only touches loaded chunks, only runs while a player is
 * nearby, and removes a capped number of blocks per tick.
 */
public final class TornadoSim {
    /** Damage radius (blocks) per EF rating. */
    public static final float[] RADIUS = {6f, 9f, 14f, 22f, 34f, 52f};
    /** Strongest block hardness destroyed at "power" thresholds. */
    private static final float[] LIMIT = {0.3f, 1.1f, 2.6f, 7f, 40f, 100000f};

    private TornadoSim() {}

    public static void tick(ServerLevel level, StormData s, RandomSource rnd, long gameTicks) {
        if (s.torInt < 0.15f) return;

        double tx = s.torX, tz = s.torZ;
        // only simulate when a player can actually be affected / is near
        boolean near = false;
        for (ServerPlayer p : level.players()) {
            double dx = p.getX() - tx, dz = p.getZ() - tz;
            if (dx * dx + dz * dz < 190.0 * 190.0) {
                near = true;
                break;
            }
        }
        if (!near) return;

        float r = s.torRadiusNow();
        int ef = s.torEf;

        if (Config.TORNADO_DESTROYS_BLOCKS.get()) destroy(level, s, rnd, tx, tz, r, ef, gameTicks);
        if (gameTicks % 2 == 0) affectEntities(level, s, tx, tz, r, ef);
    }

    // ------------------------------------------------------------------------------------

    private static void destroy(ServerLevel level, StormData s, RandomSource rnd,
                                double tx, double tz, float r, int ef, long gameTicks) {
        int budget = (int) (Config.BLOCKS_PER_TICK.get() * (0.35 + 0.65 * s.torInt) * (0.55 + 0.2 * ef));
        int debrisCap = Config.MAX_DEBRIS.get();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        double reach = r * 1.15 + 1;

        for (int i = 0; i < budget; i++) {
            double ang = rnd.nextDouble() * Math.PI * 2;
            double d = reach * Math.sqrt(rnd.nextDouble());
            int x = Mth.floor(tx + Math.cos(ang) * d);
            int z = Mth.floor(tz + Math.sin(ang) * d);
            if (level.getChunkSource().getChunkNow(x >> 4, z >> 4) == null) continue;

            double power = ef + (1.0 - d / reach) * 1.6 + (rnd.nextDouble() - 0.5) * 0.6;
            float limit = limitFor(power);
            int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) - 1;

            for (int k = 0; k < 6; k++) {
                int y = top - k;
                if (y < level.getMinBuildHeight() + 1) break;
                pos.set(x, y, z);
                BlockState st = level.getBlockState(pos);
                if (st.isAir()) continue;
                if (!st.getFluidState().isEmpty()) break;
                float hard = st.getDestroySpeed(level, pos);
                if (hard < 0 || hard > limit) break;

                boolean beAble = st.hasBlockEntity();
                if (beAble && power < 3.5) break;

                if (!beAble && DEBRIS_ROOM(debrisCap) && rnd.nextFloat() < 0.16f) {
                    BlockPos immutable = pos.immutable();
                    FallingBlockEntity fb = FallingBlockEntity.fall(level, immutable, st);
                    fb.dropItem = false;
                    double sx = Math.cos(ang), sz = Math.sin(ang);
                    double tang = 0.35 + 0.25 * s.torInt;
                    fb.setDeltaMovement(-sz * tang + sx * 0.05, 0.35 + rnd.nextDouble() * 0.5 + 0.08 * ef,
                            sx * tang + sz * 0.05);
                    fb.hurtMarked = true;
                    StormManager.DEBRIS.put(fb.getUUID(), gameTicks + 50 + rnd.nextInt(60));
                } else {
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2 | 16 | 32);
                }
            }

            // scour grass down to bare dirt at EF3+
            if (ef >= 3 && power > 3.0 && rnd.nextFloat() < 0.25f) {
                pos.set(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1, z);
                if (level.getBlockState(pos).is(Blocks.GRASS_BLOCK)) {
                    level.setBlock(pos, rnd.nextBoolean() ? Blocks.DIRT.defaultBlockState()
                            : Blocks.COARSE_DIRT.defaultBlockState(), 2);
                }
            }
        }
    }

    private static boolean DEBRIS_ROOM(int cap) {
        return StormManager.DEBRIS.size() < cap;
    }

    private static float limitFor(double power) {
        if (power < 0.8) return LIMIT[0];
        if (power < 1.6) return LIMIT[1];
        if (power < 2.6) return LIMIT[2];
        if (power < 3.6) return LIMIT[3];
        if (power < 4.6) return LIMIT[4];
        return LIMIT[5];
    }

    // ------------------------------------------------------------------------------------

    private static void affectEntities(ServerLevel level, StormData s, double tx, double tz, float r, int ef) {
        double range = r * 2.4 + 8;
        AABB box = new AABB(tx - range, s.torY - 8, tz - range, tx + range, s.torY + 140, tz + range);
        List<Entity> list = level.getEntities((Entity) null, box, e -> !e.isSpectator() && !e.isRemoved());
        int processed = 0;
        for (Entity e : list) {
            if (++processed > 90) break;
            if (e instanceof Player p && (p.isCreative() && p.getAbilities().flying)) continue;

            double dx = e.getX() - tx, dz = e.getZ() - tz;
            double dist = Math.sqrt(dx * dx + dz * dz);
            if (dist > range) continue;
            if (dist < 0.5) dist = 0.5;

            double k = Math.pow(1.0 - dist / range, 1.2) * s.torInt * (0.55 + 0.22 * ef);
            double tangX = dz / dist, tangZ = -dx / dist;          // counter-clockwise swirl
            double inX = -dx / dist, inZ = -dz / dist;

            Vec3 v = e.getDeltaMovement();
            double nx = v.x * 0.92 + (tangX * 0.20 + inX * 0.07) * k;
            double nz = v.z * 0.92 + (tangZ * 0.20 + inZ * 0.07) * k;
            double ny = v.y;
            if (dist < r * 1.5 + 4) {
                ny = Math.min(1.1, v.y + 0.10 + 0.04 * ef);
                if (e instanceof Player) e.fallDistance = Math.max(0, e.fallDistance * 0.5f);
            }
            e.setDeltaMovement(clamp(nx, 1.6), Math.min(ny, 1.4), clamp(nz, 1.6));
            e.hurtMarked = true;

            if (dist < r * 1.1 + 2 && level.getGameTime() % 10 == 0 && e instanceof net.minecraft.world.entity.LivingEntity le) {
                le.hurt(level.damageSources().generic(), 2.0f + ef * 3.5f);
            }
            if (dist < r && e instanceof net.minecraft.world.entity.item.ItemEntity && level.random.nextInt(8) == 0) {
                e.discard();
            }
        }
    }

    private static double clamp(double v, double m) {
        return Math.max(-m, Math.min(m, v));
    }

    @SuppressWarnings("unused")
    private static EntityType<?> unused() {
        return EntityType.FALLING_BLOCK;
    }
}
