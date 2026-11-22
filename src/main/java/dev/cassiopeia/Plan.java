package dev.cassiopeia;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * Where Cassiopeia's five stars land, how wide each crater is, and how fast the whole thing runs. The server makes
 * it when the skill is cast (it knows the ground), plays it, and sends this same record to the players nearby, so
 * the client's sky, particles and film are built from exactly the numbers the server digs with.
 *
 * <p>The W is the real constellation: the stars' positions are their sky positions (right ascension and declination,
 * flattened and with the overall tilt taken out so it reads as a W), and each crater is as wide as its star is bright.
 */
public record Plan(int casterId, long seed, float power, float speed, float yaw, Vec3[] stars, float[] radius)
    implements CustomPacketPayload {
    public static final Type<Plan> TYPE = new Type<>(CassiopeiaMod.id("plan"));
    public static final StreamCodec<RegistryFriendlyByteBuf, Plan> CODEC = StreamCodec.of(
        (buf, value) -> value.write(buf), Plan::read);

    /** Caph, Schedar, Gamma Cassiopeiae, Ruchbah, Segin: along the W from one end to the other. */
    public static final String[] NAMES = {"Caph", "Schedar", "Gamma Cassiopeiae", "Ruchbah", "Segin"};
    /** Across the W, 0..1 (from right ascension). */
    private static final double[] ACROSS = {0.00, 0.30, 0.45, 0.73, 1.00};
    /** Along the player's line of sight, from declination with the tilt taken out and the middle at 0. */
    private static final double[] DEPTH = {0.10, -0.202, 0.067, -0.068, 0.10};
    /** Brightness relative to Schedar (from apparent magnitudes 2.27, 2.23, 2.47, 2.68, 3.37). */
    private static final double[] FLUX = {0.96, 1.00, 0.80, 0.66, 0.35};
    /** The order the stars erupt in: dimmest first, Schedar, the brightest, as the finale. */
    public static final int[] ORDER = {4, 3, 2, 0, 1};

    /** The W's width in blocks at power 1. */
    public static final double BASE_WIDTH = 56.0;
    /** The brightest star's crater radius in blocks at power 1. */
    public static final double BASE_RADIUS = 8.5;

    /** Builds the plan for a strike aimed at {@code aim}, with the W laid across the caster's view. */
    public static Plan make(ServerLevel level, Vec3 aim, float yaw, float power, float speed, long seed, int casterId) {
        double radians = Math.toRadians(yaw);
        Vec3 forward = new Vec3(-Math.sin(radians), 0.0, Math.cos(radians));
        Vec3 right = new Vec3(-Math.cos(radians), 0.0, -Math.sin(radians));
        double width = BASE_WIDTH * power;
        Vec3[] stars = new Vec3[Timeline.STARS];
        float[] radius = new float[Timeline.STARS];
        for (int i = 0; i < Timeline.STARS; i++) {
            Vec3 flat = aim.add(right.scale((ACROSS[i] - 0.5) * width)).add(forward.scale(DEPTH[i] * width * 1.4));
            // the first free block above the ground (the star lands on the block below it)
            int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(flat.x), Mth.floor(flat.z));
            stars[i] = new Vec3(flat.x, top, flat.z);
            radius[i] = (float) Mth.clamp(BASE_RADIUS * Math.sqrt(FLUX[i]) * power, 3.0, Config.maxCraterRadius());
        }
        return new Plan(casterId, seed, power, speed, yaw, stars, radius);
    }

    @Override
    public Type<Plan> type() {
        return TYPE;
    }

    private void write(FriendlyByteBuf buf) {
        buf.writeVarInt(this.casterId);
        buf.writeLong(this.seed);
        buf.writeFloat(this.power);
        buf.writeFloat(this.speed);
        buf.writeFloat(this.yaw);
        for (int i = 0; i < Timeline.STARS; i++) {
            buf.writeDouble(this.stars[i].x);
            buf.writeDouble(this.stars[i].y);
            buf.writeDouble(this.stars[i].z);
            buf.writeFloat(this.radius[i]);
        }
    }

    private static Plan read(FriendlyByteBuf buf) {
        int casterId = buf.readVarInt();
        long seed = buf.readLong();
        float power = buf.readFloat();
        float speed = buf.readFloat();
        float yaw = buf.readFloat();
        Vec3[] stars = new Vec3[Timeline.STARS];
        float[] radius = new float[Timeline.STARS];
        for (int i = 0; i < Timeline.STARS; i++) {
            stars[i] = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
            radius[i] = buf.readFloat();
        }
        return new Plan(casterId, seed, power, speed, yaw, stars, radius);
    }

    /** The caster's view direction on the ground (the W lies across it). */
    public Vec3 forward() {
        double radians = Math.toRadians(this.yaw);
        return new Vec3(-Math.sin(radians), 0.0, Math.cos(radians));
    }

    /** The caster's right, on the ground. */
    public Vec3 right() {
        double radians = Math.toRadians(this.yaw);
        return new Vec3(-Math.cos(radians), 0.0, -Math.sin(radians));
    }

    /** The middle of the W. */
    public Vec3 centre() {
        double x = 0.0;
        double y = 0.0;
        double z = 0.0;
        for (Vec3 star : this.stars) {
            x += star.x;
            y += star.y;
            z += star.z;
        }
        return new Vec3(x / this.stars.length, y / this.stars.length, z / this.stars.length);
    }

    /** How wide the W is, in blocks. */
    public double width() {
        return BASE_WIDTH * this.power;
    }

    /** How high above its crater a risen star hangs. */
    public double skyHeight() {
        return 64.0 + 0.4 * this.width();
    }

    /** Where star {@code star} hangs once it has risen: straight above its crater, so the W reappears in the sky. */
    public Vec3 sky(int star) {
        return this.stars[star].add(0.0, this.skyHeight(), 0.0);
    }

    /** The middle of the W as it hangs in the sky. */
    public Vec3 skyCentre() {
        return this.centre().add(0.0, this.skyHeight(), 0.0);
    }

    /** Where the rising star {@code star} is, {@code s} (0..1) of the way up: quick off the ground, easing as it settles. */
    public Vec3 rise(int star, double s) {
        double left = 1.0 - Mth.clamp(s, 0.0, 1.0);
        double eased = 1.0 - left * left * left;
        return this.stars[star].add(0.0, this.skyHeight() * eased, 0.0);
    }

    /** The point {@code distance} blocks along the W as it hangs in the sky (the same path as {@link #along}). */
    public Vec3 skyAlong(double distance) {
        return this.along(distance).add(0.0, this.skyHeight(), 0.0);
    }

    /** The length of the W drawn from Caph through to Segin. */
    public double pathLength() {
        double length = 0.0;
        for (int i = 0; i < Timeline.STARS - 1; i++) {
            length += this.stars[i].distanceTo(this.stars[i + 1]);
        }
        return length;
    }

    /** The point {@code distance} blocks along the W, a little above the ground. */
    public Vec3 along(double distance) {
        double left = Math.max(0.0, distance);
        for (int i = 0; i < Timeline.STARS - 1; i++) {
            double segment = this.stars[i].distanceTo(this.stars[i + 1]);
            if (left <= segment || i == Timeline.STARS - 2) {
                double u = segment < 1.0E-6 ? 0.0 : Mth.clamp(left / segment, 0.0, 1.0);
                return this.stars[i].lerp(this.stars[i + 1], u).add(0.0, 0.5, 0.0);
            }
            left -= segment;
        }
        return this.stars[Timeline.STARS - 1];
    }
}
