package com.tttpog.stormfront.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.tttpog.stormfront.Config;
import com.tttpog.stormfront.storm.StormData;
import com.tttpog.stormfront.storm.StormType;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Draws every cloud feature of a supercell out of a few hundred shaded cumulus sprites:
 * cumulonimbus tower, anvil + mammatus, shelf cloud, rotating wall cloud, flanking line,
 * precipitation curtains, and the tornado funnel + debris cloud. Also draws a drifting field of
 * ordinary fair-weather cumulus to replace the blocky vanilla clouds.
 *
 * Far geometry is drawn right after the sky with an extended far-plane (so storms 3+ km away are
 * visible; terrain then draws over it). Anything within NEAR_DIST is drawn later, depth-tested, so it
 * is correctly hidden by hills and buildings.
 */
@Mod.EventBusSubscriber(modid = "stormfront", bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class CloudRenderer {
    // groups
    private static final int BASE = 0, TOWER = 1, ANVIL = 2, MAMMA = 3, SHELF = 4, WALL = 5, FLANK = 6, PRECIP = 7;
    // kinds
    private static final int BILL = 0, VERT = 2, FUNNEL = 3;

    private static final float H = 150f;             // base -> anvil height
    private static final float NEAR_DIST = 650f;

    private static final ResourceLocation PUFF = new ResourceLocation("stormfront", "dynamic/cumulus");
    private static final ResourceLocation STREAK = new ResourceLocation("stormfront", "dynamic/streak");
    private static final ResourceLocation RAIN = new ResourceLocation("stormfront", "dynamic/rain");
    private static boolean texReady = false;

    private static final Map<Integer, Model> MODELS = new HashMap<>();
    private static List<Item> nearItems = new ArrayList<>();

    // per-frame context
    private static double camX, camY, camZ;
    private static float timeNow;
    private static float rightX, rightY, rightZ, upX, upY, upZ;

    private CloudRenderer() {}

    // ---------------------------------------------------------------------------------------
    // model
    // ---------------------------------------------------------------------------------------

    private static final class Puff {
        byte group, kind, variant;
        float u, v, y, size, shade, rot, spin, phase, aux, alpha, squash = 1f;
    }

    private static final class Model {
        final long seed;
        final float detail;
        final List<Puff> puffs = new ArrayList<>();

        Model(long seed, float detail) {
            this.seed = seed;
            this.detail = detail;
        }
    }

    private static final class Item {
        int kind;
        ResourceLocation tex;
        float x, y, z, size, sizeY, rot, r, g, b, a, d2, squash = 1f;
        float u0, v0;
        boolean flip;
        StormData storm;
    }

    private static int cnt(int n, float detail) {
        return Math.max(1, Math.round(n * detail));
    }

    private static Puff puff(Model m, Random r, int group, int kind, float u, float v, float y,
                             float size, float shade, float alpha) {
        Puff p = new Puff();
        p.group = (byte) group;
        p.kind = (byte) kind;
        p.variant = (byte) r.nextInt(8);
        p.u = u;
        p.v = v;
        p.y = y;
        p.size = size;
        p.shade = shade;
        p.alpha = alpha;
        p.phase = r.nextFloat() * 6.28f;
        p.rot = (r.nextFloat() - 0.5f) * 0.5f;
        m.puffs.add(p);
        return p;
    }

    /** Flat-bottomed layer puff: wide and squashed, not rolled. */
    private static Puff layer(Model m, Random r, int group, float u, float v, float y, float size,
                              float squash, float shade, float alpha) {
        Puff p = puff(m, r, group, BILL, u, v, y, size, shade, alpha);
        p.squash = squash;
        p.rot = (r.nextFloat() - 0.5f) * 0.08f;
        return p;
    }

    private static Model build(StormData s, float detail) {
        Model m = new Model(s.seed, detail);
        Random r = new Random(s.seed);
        float R = s.radius;
        StormType t = s.type;

        // ---- dark flat base deck under the updraft
        for (int i = 0; i < cnt(26, detail); i++) {
            float u = clampf((float) r.nextGaussian() * 0.34f, -0.8f, 0.8f) * R;
            float v = clampf((float) r.nextGaussian() * 0.34f, -0.8f, 0.8f) * R;
            layer(m, r, BASE, u, v, 4f + r.nextFloat() * 8f, R * (0.20f + 0.16f * r.nextFloat()), 0.42f, 0.62f, 0.85f);
        }

        // ---- updraft tower, leaning downwind with height, cauliflower lobes
        for (int j = 0; j <= 15; j++) {
            float hf = j / 15f;
            int n = cnt(7, detail);
            for (int k = 0; k < n; k++) {
                float ang = r.nextFloat() * 6.2832f;
                float rad = R * (0.30f - 0.08f * hf) * (0.25f + 0.75f * (float) Math.sqrt(r.nextFloat()));
                float u = Mth.cos(ang) * rad + hf * 0.20f * R;
                float v = Mth.sin(ang) * rad;
                float sizeMul = 0.9f + 0.3f * Mth.sin(hf * 3.1416f);
                Puff p = puff(m, r, TOWER, BILL, u, v, hf * H + r.nextFloat() * 0.03f * H,
                        R * (0.15f + 0.07f * r.nextFloat()) * sizeMul,
                        0.50f * (float) Math.pow(1 - hf, 1.3) + 0.02f * r.nextFloat(), 0.97f);
                p.aux = hf;
                p.spin = (r.nextFloat() - 0.5f) * 0.002f;
            }
        }
        for (int j = 0; j <= 15; j++) {                      // small lobes on the tower's rim
            float hf = j / 15f;
            for (int k = 0; k < cnt(4, detail); k++) {
                float ang = r.nextFloat() * 6.2832f;
                float rad = R * (0.27f - 0.07f * hf) * (0.9f + 0.25f * r.nextFloat());
                Puff p = puff(m, r, TOWER, BILL, Mth.cos(ang) * rad + hf * 0.20f * R, Mth.sin(ang) * rad,
                        hf * H + r.nextFloat() * 0.03f * H, R * (0.06f + 0.05f * r.nextFloat()),
                        0.42f * (float) Math.pow(1 - hf, 1.3) + 0.10f * r.nextFloat(), 0.95f);
                p.aux = hf;
            }
        }
        for (int k = 0; k < cnt(9, detail); k++) {          // overshooting top
            float ang = r.nextFloat() * 6.2832f;
            float rad = 0.10f * R * r.nextFloat();
            Puff p = puff(m, r, TOWER, BILL, Mth.cos(ang) * rad + 0.20f * R, Mth.sin(ang) * rad,
                    H + 0.01f * H + r.nextFloat() * 0.07f * H, R * (0.07f + 0.05f * r.nextFloat()), 0.0f, 0.98f);
            p.aux = 1f;
        }

        // ---- anvil: a circular umbrella spreading all the way around the top of the updraft,
        //      centred a little downwind, drooping and thinning toward its rim, with a cirrus fringe
        float ca = Mth.cos(0.30f), sa = Mth.sin(0.30f);
        float cu = 0.28f * R * ca, cv = 0.28f * R * sa;
        for (int i = 0; i < cnt(80, detail); i++) {
            float edge = (float) Math.sqrt(r.nextFloat());               // uniform over the disc
            float th = r.nextFloat() * 6.2832f;
            float rr = edge * 1.15f * R;
            Puff p = layer(m, r, ANVIL, cu + Mth.cos(th) * rr, cv + Mth.sin(th) * rr,
                    H + (r.nextFloat() - 0.5f) * 0.03f * H - 0.05f * H * edge * edge,
                    R * (0.13f + 0.10f * r.nextFloat()) * (1.1f - 0.4f * edge), 0.32f,
                    0.26f + 0.22f * edge + 0.12f * r.nextFloat(), 0.90f - 0.35f * edge);
            p.aux = edge;
        }
        for (int i = 0; i < cnt(46, detail); i++) {                      // billowing top surface
            float edge = (float) Math.sqrt(r.nextFloat()) * 0.85f;
            float th = r.nextFloat() * 6.2832f;
            float rr = edge * 1.15f * R;
            Puff p = layer(m, r, ANVIL, cu + Mth.cos(th) * rr, cv + Mth.sin(th) * rr,
                    H + 0.025f * H + r.nextFloat() * 0.03f * H - 0.04f * H * edge * edge,
                    R * (0.08f + 0.07f * r.nextFloat()), 0.55f, 0.02f + 0.10f * edge, 0.92f);
            p.aux = edge;
        }
        for (int i = 0; i < cnt(16, detail); i++) {                      // thin cirrus rim
            float th = r.nextFloat() * 6.2832f;
            float rr = (0.95f + 0.40f * r.nextFloat()) * R;
            Puff p = layer(m, r, ANVIL, cu + Mth.cos(th) * rr, cv + Mth.sin(th) * rr,
                    H - 0.05f * H, R * (0.22f + 0.14f * r.nextFloat()), 0.16f, 0.18f, 0.26f);
            p.aux = 1f;
        }

        // ---- mammatus pouches hanging under the umbrella
        for (int i = 0; i < cnt(34, detail); i++) {
            float edge = (float) Math.sqrt(r.nextFloat()) * 0.9f;
            float th = r.nextFloat() * 6.2832f;
            float rr = edge * 1.15f * R;
            Puff p = puff(m, r, MAMMA, BILL, cu + Mth.cos(th) * rr, cv + Mth.sin(th) * rr,
                    H - 0.05f * H * edge * edge - 0.035f * H - r.nextFloat() * 0.05f * H,
                    R * 0.04f * (0.7f + 0.6f * r.nextFloat()), 0.40f, 0.85f);
            p.aux = edge;
        }

        // ---- shelf cloud: layered arc on the gust front, lowest at its leading edge
        for (int i = 0; i < cnt(60, detail); i++) {
            float ang = (r.nextFloat() - 0.5f) * 2f * 1.15f;
            float depth = r.nextFloat();
            float rad = R * (1.0f + 0.14f * depth);
            layer(m, r, SHELF, Mth.cos(ang) * rad, Mth.sin(ang) * rad,
                    -2f - 14f * depth * (0.6f + 0.4f * r.nextFloat()),
                    R * (0.10f + 0.05f * r.nextFloat()), 0.50f, 0.34f + 0.3f * depth, 0.92f);
        }
        for (int i = 0; i < cnt(12, detail); i++) {
            float ang = (r.nextFloat() - 0.5f) * 2f * 1.1f;
            float rad = R * (0.9f + 0.1f * r.nextFloat());
            layer(m, r, SHELF, Mth.cos(ang) * rad, Mth.sin(ang) * rad, 8f + r.nextFloat() * 8f,
                    R * 0.16f, 0.35f, 0.28f, 0.85f);
        }

        // ---- wall cloud (positions relative to its centre; aux = how far it hangs)
        for (int i = 0; i < cnt(18, detail); i++) {
            float ang = r.nextFloat() * 6.2832f;
            float rad = 0.07f * R * (0.2f + 0.8f * (float) Math.sqrt(r.nextFloat()));
            float f = r.nextFloat();
            Puff p = puff(m, r, WALL, BILL, Mth.cos(ang) * rad, Mth.sin(ang) * rad, 0f,
                    R * (0.045f + 0.025f * r.nextFloat()) * (1.0f - 0.3f * f),
                    0.50f + 0.30f * r.nextFloat(), 0.94f);
            p.aux = f;
        }
        {
            Puff p = layer(m, r, WALL, 0, 0, 0, R * 0.095f, 0.55f, 0.70f, 0.95f);
            p.aux = 1f;
        }

        // ---- flanking line: a staircase of towering cumulus down the rear-right flank
        for (int k = 0; k < 5; k++) {
            float bu = -0.05f * R - 0.30f * R * k;
            float bv = 0.72f * R + 0.14f * R * k;
            float ht = (0.55f - 0.09f * k) * H * 0.9f;
            for (int lvl = 0; lvl < 4; lvl++) {
                for (int q = 0; q < cnt(4, detail); q++) {
                    float ox = (r.nextFloat() - 0.5f) * 0.08f * R, oz = (r.nextFloat() - 0.5f) * 0.08f * R;
                    Puff p = puff(m, r, FLANK, BILL, bu + ox, bv + oz, lvl / 3f * ht + 4f,
                            R * (0.085f - 0.01f * k) * (1f + 0.3f * r.nextFloat()),
                            0.26f + 0.3f * (1f - lvl / 3f), 0.94f);
                    p.aux = k;
                }
            }
        }

        // ---- precipitation curtains under the core
        int n;
        float wmin, wmax, alpha, shade;
        switch (t) {
            case LP -> { n = 8; wmin = 0.05f; wmax = 0.09f; alpha = 0.30f; shade = 0.40f; }
            case HP -> { n = 30; wmin = 0.15f; wmax = 0.28f; alpha = 0.72f; shade = 0.55f; }
            default -> { n = 18; wmin = 0.12f; wmax = 0.20f; alpha = 0.52f; shade = 0.48f; }
        }
        float pu = t.puFrac * R, pv = t.pvFrac * R;
        for (int i = 0; i < cnt(n, detail); i++) {
            puff(m, r, PRECIP, VERT,
                    pu + (float) r.nextGaussian() * t.coreA * R * 0.40f,
                    pv + (float) r.nextGaussian() * t.coreB * R * 0.40f,
                    0f, R * (wmin + (wmax - wmin) * r.nextFloat()), shade, alpha);
        }
        return m;
    }

    private static float clampf(float v, float lo, float hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    private static float smoothstep(float a, float b, float x) {
        float t = clampf((x - a) / (b - a), 0f, 1f);
        return t * t * (3f - 2f * t);
    }

    // ---------------------------------------------------------------------------------------
    // frame
    // ---------------------------------------------------------------------------------------

    @SubscribeEvent
    public static void onStage(RenderLevelStageEvent e) {
        if (!Config.CLOUDS.get()) return;
        RenderLevelStageEvent.Stage st = e.getStage();
        if (st == RenderLevelStageEvent.Stage.AFTER_SKY) {
            nearItems = new ArrayList<>();
            farPass(e);
        } else if (st == RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            if (!nearItems.isEmpty()) {
                draw(e, nearItems, true);
                nearItems = new ArrayList<>();
            }
        }
    }

    private static void farPass(RenderLevelStageEvent e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        boolean ambient = Config.AMBIENT_CLOUDS.get();
        if (!ambient && !ClientStorms.hasStorms()) return;

        float pt = e.getPartialTick();
        List<StormData> storms = ClientStorms.current(pt);
        ensureTextures();

        Camera cam = e.getCamera();
        Vec3 cp = cam.getPosition();
        camX = cp.x;
        camY = cp.y;
        camZ = cp.z;
        timeNow = mc.level.getGameTime() + pt;
        Vector3f left = cam.getLeftVector(), up = cam.getUpVector();
        rightX = -left.x;
        rightY = -left.y;
        rightZ = -left.z;
        upX = up.x;
        upY = up.y;
        upZ = up.z;
        Vector3f look = cam.getLookVector();

        // sun lighting
        float tod = mc.level.getTimeOfDay(pt);
        float sunH = Mth.cos(tod * 6.2831853f);
        float bright = 0.10f + 0.90f * clampf(sunH * 1.6f + 0.30f, 0f, 1f);
        bright *= 1f - 0.18f * mc.level.getRainLevel(pt);
        float warm = clampf(1f - Math.abs(sunH) * 3.2f, 0f, 1f) * (sunH > -0.3f ? 1f : 0f);

        List<Item> all = new ArrayList<>(1500);
        float detail = Config.CLOUD_DETAIL.get().floatValue() * 1.6f;

        storms.sort((a, b) -> Double.compare(d2(a, cp), d2(b, cp)));
        int max = Math.min(storms.size(), Config.MAX_RENDERED_STORMS.get());
        for (int i = 0; i < max; i++) {
            StormData s = storms.get(i);
            Model m = MODELS.get(s.id);
            if (m == null || m.seed != s.seed || m.detail != detail) {
                m = build(s, detail);
                MODELS.put(s.id, m);
            }
            collect(s, m, all, look, bright, warm, mc);
        }
        if (MODELS.size() > 16) MODELS.clear();
        if (ambient) collectAmbient(all, look, bright, warm, detail);
        if (all.isEmpty()) return;

        all.sort((a, b) -> Float.compare(b.d2, a.d2));   // back to front
        List<Item> far = new ArrayList<>(all.size());
        List<Item> near = new ArrayList<>();
        float nd2 = NEAR_DIST * NEAR_DIST;
        for (Item it : all) {
            if (it.d2 > nd2) far.add(it);
            else near.add(it);
        }
        nearItems = near;
        if (!far.isEmpty()) draw(e, far, false);
    }

    private static double d2(StormData s, Vec3 c) {
        return (s.x - c.x) * (s.x - c.x) + (s.z - c.z) * (s.z - c.z);
    }

    // ---------------------------------------------------------------------------------------
    // ambient fair-weather cumulus (replaces the blocky vanilla clouds)
    // ---------------------------------------------------------------------------------------

    private static void collectAmbient(List<Item> out, Vector3f look, float bright, float warm, float detail) {
        final float CELL = 430f;
        final int RAD = 6;
        float cover = Config.AMBIENT_COVER.get().floatValue();
        double drift = timeNow * 0.012;                 // clouds drift east
        int ccx = Mth.floor((camX - drift) / CELL), ccz = Mth.floor(camZ / CELL);
        float rain = ClientStorms.localRain;

        for (int dx = -RAD; dx <= RAD; dx++) {
            for (int dz = -RAD; dz <= RAD; dz++) {
                int cx = ccx + dx, cz = ccz + dz;
                if (hash(cx, cz, 101) > cover) continue;
                double baseX = cx * CELL + hash(cx, cz, 102) * CELL + drift;
                double baseZ = cz * CELL + hash(cx, cz, 103) * CELL;
                float baseY = 215f + hash(cx, cz, 104) * 30f;
                float scale = 0.7f + hash(cx, cz, 106) * 0.9f;
                int n = Math.max(3, Math.round((4 + hash(cx, cz, 105) * 6f) * detail));
                for (int i = 0; i < n; i++) {
                    float a = hash(cx, cz, 200 + i) * 6.2832f;
                    float d = hash(cx, cz, 300 + i) * 75f * scale;
                    float sz = (30f + hash(cx, cz, 400 + i) * 42f) * scale;
                    float core = 1f - d / (75f * scale);                 // 1 in the middle of the cluster
                    float x = (float) (baseX + Mth.cos(a) * d - camX);
                    float z = (float) (baseZ + Mth.sin(a) * d - camZ);
                    float y = baseY + sz * 0.45f + core * sz * 0.65f - (float) camY;

                    float dist2 = x * x + y * y + z * z;
                    float dist = (float) Math.sqrt(dist2);
                    if (dist > 3600f) continue;
                    if (x * look.x + y * look.y + z * look.z < -sz * 1.3f) continue;

                    float shade = clampf(0.30f * (1f - core) + 0.10f + rain * 0.35f, 0f, 1f);
                    float cr = lerp(0.98f, 0.40f, shade), cg = lerp(0.99f, 0.43f, shade), cb = lerp(1f, 0.50f, shade);
                    float wf = warm * (1f - shade);
                    cg *= 1f - 0.07f * wf;
                    cb *= 1f - 0.18f * wf;
                    float hz = smoothstep(1800f, 3600f, dist) * 0.55f;
                    cr = lerp(cr, 0.70f, hz);
                    cg = lerp(cg, 0.76f, hz);
                    cb = lerp(cb, 0.86f, hz);
                    float a2 = (1f - smoothstep(2600f, 3600f, dist)) * smoothstep(sz * 0.15f, sz * 1.1f, dist) * 0.96f;
                    if (a2 < 0.02f) continue;

                    Item it = new Item();
                    it.kind = BILL;
                    it.tex = PUFF;
                    int variant = (int) (hash(cx, cz, 500 + i) * 8f) & 7;
                    it.u0 = (variant & 1) * 0.5f;
                    it.v0 = ((variant >> 1) & 1) * 0.5f;
                    it.flip = ((variant >> 2) & 1) == 1;
                    it.x = x;
                    it.y = y;
                    it.z = z;
                    it.size = sz;
                    it.squash = 0.78f;
                    it.rot = (hash(cx, cz, 600 + i) - 0.5f) * 0.12f;
                    it.r = cr * bright;
                    it.g = cg * bright;
                    it.b = cb * bright;
                    it.a = a2;
                    it.d2 = dist2;
                    out.add(it);
                }
            }
        }
    }

    // ---------------------------------------------------------------------------------------
    // building the per-frame sprite list
    // ---------------------------------------------------------------------------------------

    private static void collect(StormData s, Model m, List<Item> out, Vector3f look, float bright, float warm, Minecraft mc) {
        float R = s.radius;
        float base = s.baseY;
        float lower = s.wallLower();
        double wallU = s.type.wallU * R, wallV = s.type.wallV * R;
        float omega = timeNow * 0.012f * (0.4f + s.rot);
        float co = Mth.cos(omega), so = Mth.sin(omega);

        // ground height under the precipitation core (for curtains)
        float ground = 64f;
        {
            int gx = Mth.floor(s.precipX()), gz = Mth.floor(s.precipZ());
            if (mc.level.getChunkSource().hasChunk(gx >> 4, gz >> 4)) {
                ground = mc.level.getHeight(Heightmap.Types.WORLD_SURFACE, gx, gz);
            }
        }
        boolean hailTint = s.hail >= 1.5f;
        float mamFactor = s.type == StormType.HP ? 1f : (s.type == StormType.CLASSIC ? 0.8f : 0.4f);

        for (Puff p : m.puffs) {
            float ga;
            float sizeMul = 1f;
            float uu = p.u, vv = p.v;
            float yy;
            switch (p.group) {
                case BASE -> {
                    ga = s.tower;
                    yy = base + p.y;
                }
                case TOWER -> {
                    ga = clampf((s.tower - p.aux) * 6f, 0f, 1f);
                    yy = base + p.y + Mth.sin(timeNow * 0.03f + p.phase) * 1.5f;
                    sizeMul = 1f + 0.05f * Mth.sin(timeNow * 0.045f + p.phase);
                }
                case ANVIL -> {
                    ga = clampf((s.tower - 0.78f) * 4.5f, 0f, 1f);
                    float grow = 0.3f + 0.7f * ga;
                    uu *= grow;
                    vv *= grow;
                    sizeMul = grow;
                    yy = base + p.y;
                }
                case MAMMA -> {
                    ga = clampf((s.tower - 0.9f) * 10f, 0f, 1f) * clampf((s.intensity - 0.55f) * 3f, 0f, 1f) * mamFactor;
                    float grow = 0.3f + 0.7f * clampf((s.tower - 0.78f) * 4.5f, 0f, 1f);
                    uu *= grow;
                    vv *= grow;
                    yy = base + p.y + Mth.sin(timeNow * 0.02f + p.aux * 7f) * 1.0f;
                }
                case SHELF -> {
                    ga = s.shelf;
                    yy = base + p.y + Mth.sin(timeNow * 0.025f + p.phase) * 1.2f * s.shelf;
                    sizeMul = 0.6f + 0.4f * s.shelf;
                }
                case WALL -> {
                    ga = s.wall;
                    float ru = p.u * co - p.v * so, rv = p.u * so + p.v * co;
                    uu = (float) wallU + ru;
                    vv = (float) wallV + rv;
                    yy = base - lower * p.aux - 3f;
                    sizeMul = 0.6f + 0.4f * s.wall;
                }
                case FLANK -> {
                    ga = clampf(s.tower * 1.6f - 0.25f - p.aux * 0.2f, 0f, 1f);
                    yy = base + p.y + Mth.sin(timeNow * 0.03f + p.phase) * 1.2f;
                }
                default -> { // PRECIP
                    ga = (float) Math.pow(clampf(s.intensity * s.type.precip, 0f, 1f), 0.8);
                    yy = base;
                }
            }
            float alpha = p.alpha * ga * s.fade;
            if (alpha < 0.01f) continue;

            double wx = s.wx(uu, vv), wz = s.wz(uu, vv);
            float x = (float) (wx - camX), z = (float) (wz - camZ);
            float size = p.size * sizeMul;

            Item it = new Item();
            float y;
            if (p.kind == VERT) {
                float top = base - 4f, bot = Math.max(ground - 1f, 0f);
                if (top - bot < 6f) continue;
                y = (top + bot) * 0.5f - (float) camY;
                it.sizeY = (top - bot) * 0.5f;
                it.tex = RAIN;
                x += Mth.sin(timeNow * 0.02f + p.phase) * size * 0.1f;
            } else {
                y = yy - (float) camY;
                it.tex = PUFF;
                it.u0 = (p.variant & 1) * 0.5f;
                it.v0 = ((p.variant >> 1) & 1) * 0.5f;
                it.flip = ((p.variant >> 2) & 1) == 1;
            }
            float d2 = x * x + y * y + z * z;
            float dist = (float) Math.sqrt(d2);
            float dot = (x * look.x + y * look.y + z * look.z);
            if (dot < -(size + it.sizeY) * 1.2f) continue;
            if (dist > 9000f) continue;

            // colour
            float sh = clampf(p.shade + 0.08f * ClientStorms.localRain, 0f, 1f);
            float cr = lerp(0.97f, 0.22f, sh), cg = lerp(0.98f, 0.26f, sh), cb = lerp(1.00f, 0.34f, sh);
            if (hailTint && p.group == PRECIP) {
                cr *= 0.88f;
                cg *= 0.96f;
                cb *= 0.90f;
            }
            float wf = warm * (1f - 0.55f * sh);
            cg *= 1f - 0.07f * wf;
            cb *= 1f - 0.18f * wf;
            // distance haze, slightly bluer far away
            float hz = smoothstep(2500f, 8000f, dist) * 0.5f;
            cr = lerp(cr, 0.70f, hz);
            cg = lerp(cg, 0.76f, hz);
            cb = lerp(cb, 0.86f, hz);

            float a = alpha * smoothstep(size * 0.15f, size * 1.15f, dist);
            if (a < 0.01f) continue;

            it.kind = p.kind;
            it.x = x;
            it.y = y;
            it.z = z;
            it.size = size;
            it.squash = p.squash;
            it.rot = p.rot + timeNow * p.spin;
            it.r = cr * bright;
            it.g = cg * bright;
            it.b = cb * bright;
            it.a = Math.min(1f, a);
            it.d2 = d2;
            out.add(it);
        }

        // ---- tornado funnel + debris cloud
        if (s.tornado && s.torInt > 0.05f) {
            float rr = s.torRadiusNow();
            Item f = new Item();
            f.kind = FUNNEL;
            f.tex = STREAK;
            f.storm = s;
            float topY = base - lower - 2f;
            float midY = (topY + s.torY) * 0.5f;
            f.x = (float) (s.torX - camX);
            f.y = midY - (float) camY;
            f.z = (float) (s.torZ - camZ);
            f.d2 = f.x * f.x + f.y * f.y + f.z * f.z;
            f.r = bright;
            f.g = bright;
            f.b = bright;
            f.a = s.fade;
            f.size = bright;
            out.add(f);

            int n = Math.max(6, Math.round(14 * Config.CLOUD_DETAIL.get().floatValue()));
            for (int i = 0; i < n; i++) {
                float ang = timeNow * 0.07f + i * 6.2832f / n;
                float rad = rr * (0.7f + 0.5f * Mth.sin(i * 2.3f));
                float size = rr * (0.9f + 0.4f * Mth.sin(i * 1.7f + 1f)) + 3f;
                Item d = new Item();
                d.kind = BILL;
                d.tex = PUFF;
                d.u0 = (i & 1) * 0.5f;
                d.v0 = ((i >> 1) & 1) * 0.5f;
                d.flip = ((i >> 2) & 1) == 1;
                d.x = (float) (s.torX - camX) + Mth.cos(ang) * rad;
                d.z = (float) (s.torZ - camZ) + Mth.sin(ang) * rad;
                d.y = s.torY + size * 0.45f + (i % 3) * rr * 0.35f - (float) camY;
                d.size = size;
                d.rot = ang;
                d.r = 0.30f * bright;
                d.g = 0.25f * bright;
                d.b = 0.20f * bright;
                d.a = 0.60f * Math.min(1f, s.torInt * 1.6f) * s.fade * smoothstep(size * 0.15f, size * 1.15f,
                        (float) Math.sqrt(d.x * d.x + d.y * d.y + d.z * d.z));
                d.d2 = d.x * d.x + d.y * d.y + d.z * d.z;
                if (d.a > 0.01f) out.add(d);
            }
        }
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    // ---------------------------------------------------------------------------------------
    // drawing
    // ---------------------------------------------------------------------------------------

    private static void draw(RenderLevelStageEvent e, List<Item> items, boolean nearPass) {
        Matrix4f orig = e.getProjectionMatrix();
        Matrix4f mat = e.getPoseStack().last().pose();

        if (!nearPass) {
            float fovy = 2f * (float) Math.atan(1.0 / orig.m11());
            float aspect = orig.m11() / orig.m00();
            Matrix4f ext = new Matrix4f().setPerspective(fovy, aspect, 0.05f, 12000f);
            RenderSystem.setProjectionMatrix(ext, VertexSorting.DISTANCE_TO_ORIGIN);
            RenderSystem.disableDepthTest();
        } else {
            RenderSystem.enableDepthTest();
        }
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);

        BufferBuilder bb = Tesselator.getInstance().getBuilder();
        ResourceLocation cur = null;
        boolean open = false;
        for (Item it : items) {
            if (it.tex != cur) {
                if (open) BufferUploader.drawWithShader(bb.end());
                RenderSystem.setShaderTexture(0, it.tex);
                bb.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
                open = true;
                cur = it.tex;
            }
            switch (it.kind) {
                case BILL -> {
                    float c = Mth.cos(it.rot), s = Mth.sin(it.rot);
                    float ax = (rightX * c + upX * s) * it.size, ay = (rightY * c + upY * s) * it.size, az = (rightZ * c + upZ * s) * it.size;
                    float k = it.size * it.squash;
                    float bx = (-rightX * s + upX * c) * k, by = (-rightY * s + upY * c) * k, bz = (-rightZ * s + upZ * c) * k;
                    quad(bb, mat, it.x, it.y, it.z, ax, ay, az, bx, by, bz, it.flip ? it.u0 + 0.5f : it.u0, it.v0, it.flip ? -0.5f : 0.5f, it.r, it.g, it.b, it.a);
                }
                case VERT -> {
                    float len = (float) Math.sqrt(it.x * it.x + it.z * it.z);
                    float hx = len < 1e-3f ? 1f : -it.z / len, hz = len < 1e-3f ? 0f : it.x / len;
                    quad(bb, mat, it.x, it.y, it.z, hx * it.size, 0, hz * it.size, 0, it.sizeY, 0,
                            0f, 0f, 1f, it.r, it.g, it.b, it.a);
                }
                case FUNNEL -> funnel(bb, mat, it);
                default -> { }
            }
        }
        if (open) BufferUploader.drawWithShader(bb.end());

        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        if (!nearPass) {
            RenderSystem.setProjectionMatrix(orig, VertexSorting.DISTANCE_TO_ORIGIN);
        }
    }

    private static void quad(BufferBuilder bb, Matrix4f m, float cx, float cy, float cz,
                             float ax, float ay, float az, float bx, float by, float bz,
                             float u0, float v0, float span,
                             float r, float g, float b, float a) {
        float u1 = u0 + span, v1 = v0 + Math.abs(span);
        float vv0 = v0;
        bb.vertex(m, cx - ax - bx, cy - ay - by, cz - az - bz).uv(u0, v1).color(r, g, b, a).endVertex();
        bb.vertex(m, cx + ax - bx, cy + ay - by, cz + az - bz).uv(u1, v1).color(r, g, b, a).endVertex();
        bb.vertex(m, cx + ax + bx, cy + ay + by, cz + az + bz).uv(u1, v0).color(r, g, b, a).endVertex();
        bb.vertex(m, cx - ax + bx, cy - ay + by, cz - az + bz).uv(u0, vv0).color(r, g, b, a).endVertex();
    }

    /** Condensation funnel: a tapered, kinked, rotating tube (plus a wider faint halo shell). */
    private static void funnel(BufferBuilder bb, Matrix4f m, Item it) {
        StormData s = it.storm;
        float bright = it.size;
        float base = s.baseY;
        float topY = base - s.wallLower() - 2f;
        float total = topY - s.torY;
        if (total < 4f) return;

        float desc = smoothstep(0f, 0.55f, s.torInt);
        float botY = topY - total * desc;
        float rr = s.torRadiusNow();
        float rTop = 5f + rr * 2.2f;
        float rBot = Math.max(1.2f, rr * 0.28f * (0.4f + 0.6f * s.torInt));
        float rope = 1f - smoothstep(0.25f, 0.75f, s.torInt);

        final int RINGS = 18, SEGS = 14;
        float[] ox = new float[RINGS + 1], oz = new float[RINGS + 1], oy = new float[RINGS + 1], rad = new float[RINGS + 1];
        for (int i = 0; i <= RINGS; i++) {
            float hh = i / (float) RINGS;
            oy[i] = botY + (topY - botY) * hh;
            float prof = (float) Math.pow(hh, 1.7f - 0.5f * rope);
            rad[i] = (rBot + (rTop - rBot) * prof) * (1f - 0.45f * rope * (1f - hh));
            float amp = (1f - hh) * total * (0.04f + 0.20f * rope) * desc;
            ox[i] = (float) (s.torX - camX) + Mth.sin(hh * 4.1f + timeNow * 0.035f) * amp + (1f - hh) * 0.06f * total * (float) s.hx();
            oz[i] = (float) (s.torZ - camZ) + Mth.cos(hh * 3.3f + timeNow * 0.027f + 1.7f) * amp + (1f - hh) * 0.06f * total * (float) s.hz();
        }
        for (int shell = 0; shell < 2; shell++) {
            float scl = shell == 0 ? 1f : 1.45f;
            float alpha = (shell == 0 ? 0.90f : 0.26f) * s.fade * smoothstep(0.02f, 0.2f, s.torInt);
            for (int i = 0; i < RINGS; i++) {
                float h0 = i / (float) RINGS, h1 = (i + 1) / (float) RINGS;
                float d0 = (1f - h0) * (1f - h0), d1 = (1f - h1) * (1f - h1);
                float[] c0 = funnelColor(d0, bright), c1 = funnelColor(d1, bright);
                for (int j = 0; j < SEGS; j++) {
                    float a0 = j / (float) SEGS * 6.2832f + timeNow * 0.35f * (1f + (1f - h0));
                    float a1 = (j + 1) / (float) SEGS * 6.2832f + timeNow * 0.35f * (1f + (1f - h0));
                    float a0b = j / (float) SEGS * 6.2832f + timeNow * 0.35f * (1f + (1f - h1));
                    float a1b = (j + 1) / (float) SEGS * 6.2832f + timeNow * 0.35f * (1f + (1f - h1));
                    float u0 = j / (float) SEGS * 2f, u1 = (j + 1) / (float) SEGS * 2f;
                    float vv0 = h0 * 3f + timeNow * 0.01f, vv1 = h1 * 3f + timeNow * 0.01f;
                    bb.vertex(m, ox[i] + Mth.cos(a0) * rad[i] * scl, oy[i] - (float) camY, oz[i] + Mth.sin(a0) * rad[i] * scl)
                            .uv(u0, vv0).color(c0[0], c0[1], c0[2], alpha).endVertex();
                    bb.vertex(m, ox[i] + Mth.cos(a1) * rad[i] * scl, oy[i] - (float) camY, oz[i] + Mth.sin(a1) * rad[i] * scl)
                            .uv(u1, vv0).color(c0[0], c0[1], c0[2], alpha).endVertex();
                    bb.vertex(m, ox[i + 1] + Mth.cos(a1b) * rad[i + 1] * scl, oy[i + 1] - (float) camY, oz[i + 1] + Mth.sin(a1b) * rad[i + 1] * scl)
                            .uv(u1, vv1).color(c1[0], c1[1], c1[2], alpha).endVertex();
                    bb.vertex(m, ox[i + 1] + Mth.cos(a0b) * rad[i + 1] * scl, oy[i + 1] - (float) camY, oz[i + 1] + Mth.sin(a0b) * rad[i + 1] * scl)
                            .uv(u0, vv1).color(c1[0], c1[1], c1[2], alpha).endVertex();
                }
            }
        }
    }

    private static float[] funnelColor(float dirty, float bright) {
        float r = lerp(0.50f, 0.27f, dirty), g = lerp(0.52f, 0.23f, dirty), b = lerp(0.56f, 0.19f, dirty);
        return new float[]{r * bright, g * bright, b * bright};
    }

    // ---------------------------------------------------------------------------------------
    // procedural textures (nothing to ship, nothing to load)
    // ---------------------------------------------------------------------------------------

    private static void ensureTextures() {
        if (texReady) return;
        Minecraft mc = Minecraft.getInstance();
        mc.getTextureManager().register(PUFF, makeCumulusAtlas());
        mc.getTextureManager().register(STREAK, makeStreak());
        mc.getTextureManager().register(RAIN, makeRain());
        texReady = true;
    }

    private static float hash(int x, int y, int seed) {
        int h = x * 374761393 + y * 668265263 + seed * 144665;
        h = (h ^ (h >>> 13)) * 1274126177;
        h ^= (h >>> 16);
        return (h & 0xFFFF) / 65535f;
    }

    private static float vnoise(float x, float y, int seed, int perX, int perY) {
        int x0 = (int) Math.floor(x), y0 = (int) Math.floor(y);
        float fx = x - x0, fy = y - y0;
        fx = fx * fx * (3 - 2 * fx);
        fy = fy * fy * (3 - 2 * fy);
        int xa = Math.floorMod(x0, perX), xb = Math.floorMod(x0 + 1, perX);
        int ya = Math.floorMod(y0, perY), yb = Math.floorMod(y0 + 1, perY);
        float a = hash(xa, ya, seed), b = hash(xb, ya, seed), c = hash(xa, yb, seed), d = hash(xb, yb, seed);
        float top = a + (b - a) * fx, bot = c + (d - c) * fx;
        return top + (bot - top) * fy;
    }

    private static int abgr(float r, float g, float b, float a) {
        int ir = (int) (clampf(r, 0f, 1f) * 255), ig = (int) (clampf(g, 0f, 1f) * 255);
        int ib = (int) (clampf(b, 0f, 1f) * 255), ia = (int) (clampf(a, 0f, 1f) * 255);
        return (ia << 24) | (ib << 16) | (ig << 8) | ir;
    }

    private static DynamicTexture finish(NativeImage img) {
        DynamicTexture t = new DynamicTexture(img);
        t.bind();
        t.setFilter(true, false);
        t.upload();
        return t;
    }

    private static float fbm(float x, float y, int seed, int octaves) {
        float sum = 0f, amp = 0.5f, norm = 0f;
        for (int o = 0; o < octaves; o++) {
            sum += amp * vnoise(x, y, seed + o * 17, 4096, 4096);
            norm += amp;
            x *= 2.03f;
            y *= 2.03f;
            amp *= 0.5f;
        }
        return sum / norm;
    }

    /**
     * 2x2 atlas of cloud sprites (each 256x256). Density comes from a domain-warped "billow" noise
     * (rounded cauliflower bumps) with turbulent, ragged edges and a flattened bottom. Shading is a
     * cheap self-shadow: each pixel marches toward the sun through its own density, so lit tops, grey
     * undersides and dark creases appear. All of this is baked once at startup, so it costs nothing per frame.
     */
    private static DynamicTexture makeCumulusAtlas() {
        final int T = 256, N = T * 2;
        NativeImage img = new NativeImage(NativeImage.Format.RGBA, N, N, false);
        float[] dens = new float[T * T];
        for (int variant = 0; variant < 4; variant++) {
            int seed = 101 + variant * 37;
            for (int y = 0; y < T; y++) {
                for (int x = 0; x < T; x++) {
                    float nx = (x + 0.5f) / T, ny = (y + 0.5f) / T;
                    float cx = (nx - 0.5f) * 2f, cy = (ny - 0.5f) * 2f;
                    float wx = fbm(nx * 3f + 5f, ny * 3f, seed, 3) - 0.5f;
                    float wy = fbm(nx * 3f, ny * 3f + 9f, seed + 5, 3) - 0.5f;
                    float px = cx + wx * 0.55f, py = cy + wy * 0.55f;
                    float rr = (float) Math.sqrt(px * px + py * py);
                    if (py > 0.25f) rr *= 1f + (py - 0.25f) * 1.5f;               // flat-ish bottom
                    float base = 1f - rr / 0.86f;
                    float billow = 1f - Math.abs(2f * fbm(nx * 5f, ny * 5f, seed + 11, 4) - 1f);
                    float fine = fbm(nx * 16f, ny * 16f, seed + 23, 3) - 0.5f;
                    float d = base * 1.15f + (billow - 0.55f) * 0.62f + fine * 0.34f;
                    float cxr = (float) Math.sqrt(cx * cx + cy * cy);
                    d *= 1f - smoothstep(0.80f, 1.0f, cxr);                       // never touch the sprite border
                    dens[y * T + x] = clampf(d, 0f, 1f);
                }
            }
            int ox0 = (variant & 1) * T, oy0 = (variant >> 1) * T;
            for (int y = 0; y < T; y++) {
                for (int x = 0; x < T; x++) {
                    float d = dens[y * T + x];
                    float a = smoothstep(0.04f, 0.42f, d);
                    float lit = 1f;
                    if (a > 0.004f) {
                        float acc = 0f;                                           // march toward the sun (upper left)
                        float sx = x, sy = y;
                        for (int k = 1; k <= 14; k++) {
                            sx -= 3.2f;
                            sy -= 6.0f / 2.0f * 1.0f;
                            int ix = (int) sx, iy = (int) sy;
                            if (ix < 0 || iy < 0 || ix >= T || iy >= T) break;
                            acc += dens[iy * T + ix];
                        }
                        lit = (float) Math.exp(-acc * 0.16f);
                    }
                    float shade = 0.40f + 0.60f * lit;
                    shade *= 0.80f + 0.20f * (1f - y / (float) T);                // greyer toward the base
                    shade += 0.10f * (1f - smoothstep(0.0f, 0.35f, d));           // bright, thin edges (silver lining)
                    float cr = shade * 0.965f, cg = shade * 0.985f, cb = shade;
                    img.setPixelRGBA(ox0 + x, oy0 + y, abgr(cr, cg, cb, a));
                }
            }
        }
        return finish(img);
    }

    private static DynamicTexture makeStreak() {
        int N = 64;
        NativeImage img = new NativeImage(NativeImage.Format.RGBA, N, N, false);
        for (int y = 0; y < N; y++) {
            for (int x = 0; x < N; x++) {
                float n = vnoise(x * 0.14f, y * 0.045f, 5, 9, 3) * 0.6f + vnoise(x * 0.4f, y * 0.12f, 6, 26, 8) * 0.4f;
                float g = 0.62f + 0.38f * n;
                img.setPixelRGBA(x, y, abgr(g, g, g, 1f));
            }
        }
        return finish(img);
    }

    private static DynamicTexture makeRain() {
        int N = 64;
        NativeImage img = new NativeImage(NativeImage.Format.RGBA, N, N, false);
        for (int y = 0; y < N; y++) {
            for (int x = 0; x < N; x++) {
                float dx = (x - 31.5f) / 24f;
                float across = (float) Math.exp(-dx * dx * 1.6);
                float n = vnoise(x * 0.20f, y * 0.04f, 11, 64, 4) * 0.55f + vnoise(x * 0.5f, y * 0.09f, 12, 64, 8) * 0.45f;
                float a = across * (0.25f + 0.75f * n);
                float edgeY = smoothstep(0f, 0.12f, y / (float) N) * (0.55f + 0.45f * smoothstep(1f, 0.55f, y / (float) N));
                img.setPixelRGBA(x, y, abgr(0.88f, 0.9f, 0.93f, a * edgeY));
            }
        }
        return finish(img);
    }
}
