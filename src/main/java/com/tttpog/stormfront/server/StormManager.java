package com.tttpog.stormfront.server;

import com.tttpog.stormfront.Config;
import com.tttpog.stormfront.Net;
import com.tttpog.stormfront.storm.StormData;
import com.tttpog.stormfront.storm.StormType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Server-side storm simulation. Runs only in the Overworld and only while somebody is online.
 * Storms are plain data (no entities, no chunk loading), so they are very cheap.
 */
@Mod.EventBusSubscriber(modid = "stormfront", bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class StormManager {
    public static final List<StormData> STORMS = new ArrayList<>();
    /** debris entity uuid -> game tick at which it must be removed */
    public static final Map<UUID, Long> DEBRIS = new HashMap<>();

    private static int nextId = 1;
    private static long ticks = 0;
    private static int spawnTimer = 1200;
    private static final double SIM_RANGE = 7000.0;

    private StormManager() {}

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.level instanceof ServerLevel level)) return;
        if (level.dimension() != Level.OVERWORLD) return;
        List<ServerPlayer> players = level.players();
        if (players.isEmpty()) return;

        ticks++;
        RandomSource rnd = level.getRandom();

        if (Config.AUTO_SPAWN.get() && --spawnTimer <= 0) {
            spawnTimer = Config.SPAWN_MINUTES.get() * 1200 / 2 + rnd.nextInt(Config.SPAWN_MINUTES.get() * 1200);
            if (STORMS.size() < Config.MAX_STORMS.get()) {
                spawnAroundPlayer(level, players.get(rnd.nextInt(players.size())), null, false);
            }
        }

        Iterator<StormData> it = STORMS.iterator();
        while (it.hasNext()) {
            StormData s = it.next();
            advance(level, s, rnd);
            boolean near = false;
            for (ServerPlayer p : players) {
                double dx = p.getX() - s.x, dz = p.getZ() - s.z;
                if (dx * dx + dz * dz < SIM_RANGE * SIM_RANGE) {
                    near = true;
                    break;
                }
            }
            if (s.age > s.life || !near) {
                it.remove();
                continue;
            }
            if (s.tornado) TornadoSim.tick(level, s, rnd, ticks);
        }

        if (ticks % 2 == 0) cleanupDebris(level);
        if (ticks % 5 == 0) syncAll(players);
    }

    @SubscribeEvent
    public static void onStop(ServerStoppedEvent e) {
        STORMS.clear();
        DEBRIS.clear();
        spawnTimer = 1200;
    }

    // ------------------------------------------------------------------------------------

    public static StormData spawnAroundPlayer(ServerLevel level, ServerPlayer p, StormType forced, boolean mature) {
        RandomSource rnd = level.getRandom();
        StormType type = forced;
        if (type == null) {
            float r = rnd.nextFloat();
            type = r < 0.40f ? StormType.CLASSIC : (r < 0.70f ? StormType.HP : StormType.LP);
        }
        double ang = rnd.nextDouble() * Math.PI * 2;
        double dist = 1900 + rnd.nextDouble() * 900;
        double sx = p.getX() + Math.cos(ang) * dist;
        double sz = p.getZ() + Math.sin(ang) * dist;
        return spawn(level, type, sx, sz, p.getX(), p.getZ(), 500, mature);
    }

    /** Spawn a storm at (sx, sz) heading toward (tx, tz) +- scatter blocks. */
    public static StormData spawn(ServerLevel level, StormType type, double sx, double sz,
                                  double tx, double tz, double scatter, boolean mature) {
        RandomSource rnd = level.getRandom();
        tx += (rnd.nextDouble() - 0.5) * 2 * scatter;
        tz += (rnd.nextDouble() - 0.5) * 2 * scatter;
        StormData s = new StormData();
        s.id = nextId++;
        s.type = type;
        s.x = sx;
        s.z = sz;
        s.dir = (float) Math.atan2(tz - sz, tx - sx);
        s.speed = (float) ((0.28 + rnd.nextDouble() * 0.22) * Config.SPEED.get());
        s.radius = type.radius * (0.85f + rnd.nextFloat() * 0.3f);
        s.baseY = Config.CLOUD_BASE_Y.get() + type.baseDY;
        s.seed = rnd.nextLong();
        s.life = 16000 + rnd.nextInt(10000);
        s.turn = (rnd.nextFloat() - 0.4f) * 0.00005f;     // right-movers veer clockwise slightly
        s.wanderPhase = rnd.nextFloat() * 6.28f;
        s.fade = 1f;
        s.torCooldown = 600 + rnd.nextInt(1200);
        if (mature) s.age = (int) (s.life * 0.33f);
        STORMS.add(s);
        return s;
    }

    // ------------------------------------------------------------------------------------

    private static void advance(ServerLevel level, StormData s, RandomSource rnd) {
        s.age++;
        s.dir += s.turn;
        s.x += s.hx() * s.speed;
        s.z += s.hz() * s.speed;

        float lf = s.age / (float) s.life;
        float growth = smooth(lf / 0.16f);
        float decay = 1f - smooth((lf - 0.78f) / 0.22f);
        s.intensity = growth * decay;
        s.tower = growth;
        s.fade = Math.max(0.0f, decay);

        float pulse = 0.85f + 0.15f * Mth.sin(s.age * 0.0021f + s.wanderPhase);
        s.rot = Mth.clamp(s.type.rotFactor * smooth((lf - 0.14f) / 0.18f) * decay * pulse, 0f, 1f);
        s.shelf = Mth.clamp(s.type.shelfFactor * smooth((lf - 0.18f) / 0.2f) * decay, 0f, 1f);
        s.wall = Mth.clamp(s.type.wallFactor * smooth((s.rot - 0.25f) / 0.45f), 0f, 1f);
        s.hail = s.type.hailMax * smooth((lf - 0.16f) / 0.14f) * decay * (0.72f + 0.28f * Mth.sin(s.age * 0.0017f + 1.3f + s.wanderPhase));

        // ---- tornado life-cycle ----
        if (s.tornado) {
            s.torAge++;
            float up = smooth(s.torAge / 420f);
            float down = smooth((s.torLife - s.torAge) / 380f);
            s.torInt = Math.min(up, down);
            double ph = s.wanderPhase;
            double wx = Math.sin(s.age * 0.0113 + ph) * 22 + Math.sin(s.age * 0.031 + ph * 2) * 8;
            double wz = Math.cos(s.age * 0.0097 + ph) * 22 + Math.cos(s.age * 0.027) * 8;
            s.torX = (float) (s.wallX() + wx);
            s.torZ = (float) (s.wallZ() + wz);
            updateGround(level, s);
            if (s.torAge >= s.torLife || s.wall < 0.1f) {
                s.tornado = false;
                s.torInt = 0;
                s.torCooldown = 3000 + rnd.nextInt(5000);
            }
        } else {
            if (s.torCooldown > 0) s.torCooldown--;
            boolean ready = s.rot > 0.55f && s.wall > 0.4f && s.torCooldown <= 0;
            if (s.forceTornado) {
                startTornado(level, s, rnd, s.forcedEf);
                s.forceTornado = false;
            } else if (ready && s.age % 100 == 0 && s.type.tornadoFactor > 0.2f
                    && rnd.nextFloat() < Config.TORNADO_CHANCE.get() * s.type.tornadoFactor * 0.55) {
                startTornado(level, s, rnd, -1);
            } else if (ready && s.age % 100 == 0) {
                s.torCooldown = 1800;
            }
        }
    }

    public static void startTornado(ServerLevel level, StormData s, RandomSource rnd, int ef) {
        if (ef < 0) ef = rollEf(s.type, rnd);
        ef = Mth.clamp(ef, 0, 5);
        s.tornado = true;
        s.torEf = ef;
        s.torAge = 0;
        s.torLife = 2200 + rnd.nextInt(3200) + ef * 250;
        s.torRad = TornadoSim.RADIUS[ef] * (0.85f + rnd.nextFloat() * 0.35f);
        s.torInt = 0f;
        s.torX = (float) s.wallX();
        s.torZ = (float) s.wallZ();
        updateGround(level, s);
        // a forced tornado needs a mature wall cloud right away
        if (s.wall < 0.6f) {
            s.age = Math.max(s.age, (int) (s.life * 0.32f));
        }
    }

    private static int rollEf(StormType t, RandomSource rnd) {
        float[] w = switch (t) {
            case LP -> new float[]{50, 30, 14, 5, 1, 0};
            case CLASSIC -> new float[]{22, 24, 22, 16, 11, 5};
            case HP -> new float[]{18, 22, 22, 18, 13, 7};
        };
        float sum = 0;
        for (float f : w) sum += f;
        float r = rnd.nextFloat() * sum;
        int ef = 0;
        for (int i = 0; i < w.length; i++) {
            r -= w[i];
            if (r <= 0) {
                ef = i;
                break;
            }
        }
        double bias = Config.TORNADO_STRENGTH_BIAS.get();
        if (bias > 1.0 && rnd.nextFloat() < (bias - 1.0) * 0.4) ef++;
        return Math.min(5, ef);
    }

    static void updateGround(ServerLevel level, StormData s) {
        int cx = Mth.floor(s.torX) >> 4, cz = Mth.floor(s.torZ) >> 4;
        if (level.getChunkSource().getChunkNow(cx, cz) != null) {
            s.torY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(s.torX), Mth.floor(s.torZ));
        } else if (s.torY == 0) {
            s.torY = 64;
        }
    }

    private static void cleanupDebris(ServerLevel level) {
        if (DEBRIS.isEmpty()) return;
        Iterator<Map.Entry<UUID, Long>> it = DEBRIS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Long> en = it.next();
            Entity ent = level.getEntity(en.getKey());
            if (ent == null || ent.isRemoved()) {
                it.remove();
            } else if (ticks > en.getValue()) {
                ent.discard();
                it.remove();
            }
        }
    }

    private static void syncAll(List<ServerPlayer> players) {
        for (ServerPlayer p : players) {
            List<StormData> list = new ArrayList<>();
            for (StormData s : STORMS) {
                double dx = p.getX() - s.x, dz = p.getZ() - s.z;
                if (dx * dx + dz * dz < 9000.0 * 9000.0) list.add(s);
            }
            Net.sendTo(p, list);
        }
    }

    static float smooth(float t) {
        t = Mth.clamp(t, 0f, 1f);
        return t * t * (3f - 2f * t);
    }

    public static long ticks() {
        return ticks;
    }

    public static StormData nearest(double x, double z) {
        StormData best = null;
        double bd = Double.MAX_VALUE;
        for (StormData s : STORMS) {
            double d = (s.x - x) * (s.x - x) + (s.z - z) * (s.z - z);
            if (d < bd) {
                bd = d;
                best = s;
            }
        }
        return best;
    }

    public static BlockPos posOf(StormData s) {
        return BlockPos.containing(s.x, s.baseY, s.z);
    }
}
