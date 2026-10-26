package com.tttpog.stormfront.storm;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.util.Mth;

/**
 * One supercell. The server owns and mutates these; the synced subset is sent to clients,
 * which extrapolate the motion between packets.
 */
public class StormData {
    // ---- synced -------------------------------------------------------------------------
    public int id;
    public StormType type = StormType.CLASSIC;
    public double x, z;           // updraft centre
    public float dir;             // heading (radians); direction = (cos, sin) in (x, z)
    public float speed;           // blocks / tick
    public float radius;
    public float intensity;       // overall strength 0..1 (growth * decay)
    public float tower;           // tower growth 0..1
    public float rot;             // mesocyclone strength 0..1
    public float shelf;           // shelf cloud development 0..1
    public float wall;            // wall cloud development 0..1
    public float hail;            // max hail size in inches (0 = none)
    public float fade;            // 1 normally, falls to 0 as the storm dies
    public int baseY;             // cloud base height
    public long seed;

    public boolean tornado;
    public float torX, torZ, torY;
    public float torInt;          // 0..1 life-cycle strength
    public float torRad;          // full-strength damage radius in blocks
    public int torEf;

    // ---- server only --------------------------------------------------------------------
    public int age, life;
    public float turn;            // slow veering (rad / tick)
    public int torAge, torLife;
    public int torCooldown = 1200;
    public boolean forceTornado;
    public int forcedEf = -1;
    public float wanderPhase;

    // ---- geometry helpers -----------------------------------------------------------------
    public double hx() { return Math.cos(dir); }
    public double hz() { return Math.sin(dir); }
    public double rx() { return -Math.sin(dir); }
    public double rz() { return Math.cos(dir); }

    public double wx(double u, double v) { return x + u * hx() + v * rx(); }
    public double wz(double u, double v) { return z + u * hz() + v * rz(); }

    public double wallX() { return wx(type.wallU * radius, type.wallV * radius); }
    public double wallZ() { return wz(type.wallU * radius, type.wallV * radius); }
    public double precipX() { return wx(type.puFrac * radius, type.pvFrac * radius); }
    public double precipZ() { return wz(type.puFrac * radius, type.pvFrac * radius); }

    /** Height (blocks) the wall cloud hangs below the cloud base. */
    public float wallLower() { return 0.115f * radius * wall; }

    /** Rain / precipitation intensity 0..1 at a world position. */
    public float precipAt(double px, double pz) {
        double dx = px - x, dz = pz - z;
        double du = dx * hx() + dz * hz() - type.puFrac * radius;
        double dv = dx * rx() + dz * rz() - type.pvFrac * radius;
        double a = type.coreA * radius, b = type.coreB * radius;
        double f = Math.exp(-1.3 * (du * du / (a * a) + dv * dv / (b * b)));
        double s = 1.5 * radius;
        double shield = type.shield * Math.exp(-0.5 * (du * du + dv * dv) / (s * s));
        return Mth.clamp((float) ((f * type.precip + shield) * intensity), 0f, 1f);
    }

    /** Hail intensity 0..1 (core of the precip shaft nearest the updraft). */
    public float hailAt(double px, double pz) {
        if (hail < 0.2f) return 0f;
        double dx = px - x, dz = pz - z;
        double a = type.coreA * radius, b = type.coreB * radius;
        double du = dx * hx() + dz * hz() - (type.puFrac * radius - 0.3 * a);
        double dv = dx * rx() + dz * rz() - (type.pvFrac * radius + 0.1 * b);
        double f = Math.exp(-1.8 * (du * du / (0.25 * a * a) + dv * dv / (0.30 * b * b)));
        return Mth.clamp((float) (f * Math.min(1.0, hail / 0.6) * intensity), 0f, 1f);
    }

    /** Overhead cloud cover / darkness 0..1. */
    public float coverAt(double px, double pz) {
        double dx = px - x, dz = pz - z;
        double d = Math.sqrt(dx * dx + dz * dz) / (1.15 * radius);
        return (float) (Math.exp(-d * d) * tower * intensity);
    }

    /** Effective tornado radius (blocks) right now. */
    public float torRadiusNow() {
        return torRad * (0.25f + 0.75f * (float) Math.pow(torInt, 0.7));
    }

    public StormData copy() {
        StormData c = new StormData();
        c.id = id; c.type = type; c.x = x; c.z = z; c.dir = dir; c.speed = speed; c.radius = radius;
        c.intensity = intensity; c.tower = tower; c.rot = rot; c.shelf = shelf; c.wall = wall;
        c.hail = hail; c.fade = fade; c.baseY = baseY; c.seed = seed;
        c.tornado = tornado; c.torX = torX; c.torZ = torZ; c.torY = torY; c.torInt = torInt;
        c.torRad = torRad; c.torEf = torEf;
        return c;
    }

    /** Copy advanced by dt ticks of straight-line motion (client extrapolation). */
    public StormData copyAdvanced(double dt) {
        StormData c = copy();
        double mx = hx() * speed * dt, mz = hz() * speed * dt;
        c.x += mx;
        c.z += mz;
        c.torX += (float) mx;
        c.torZ += (float) mz;
        return c;
    }

    // ---- network -------------------------------------------------------------------------
    public void write(FriendlyByteBuf b) {
        b.writeVarInt(id);
        b.writeByte(type.ordinal());
        b.writeDouble(x);
        b.writeDouble(z);
        b.writeFloat(dir);
        b.writeFloat(speed);
        b.writeFloat(radius);
        b.writeFloat(intensity);
        b.writeFloat(tower);
        b.writeFloat(rot);
        b.writeFloat(shelf);
        b.writeFloat(wall);
        b.writeFloat(hail);
        b.writeFloat(fade);
        b.writeVarInt(baseY);
        b.writeLong(seed);
        b.writeBoolean(tornado);
        if (tornado) {
            b.writeFloat(torX);
            b.writeFloat(torZ);
            b.writeFloat(torY);
            b.writeFloat(torInt);
            b.writeFloat(torRad);
            b.writeByte(torEf);
        }
    }

    public static StormData read(FriendlyByteBuf b) {
        StormData s = new StormData();
        s.id = b.readVarInt();
        s.type = StormType.values()[Mth.clamp(b.readByte(), 0, StormType.values().length - 1)];
        s.x = b.readDouble();
        s.z = b.readDouble();
        s.dir = b.readFloat();
        s.speed = b.readFloat();
        s.radius = b.readFloat();
        s.intensity = b.readFloat();
        s.tower = b.readFloat();
        s.rot = b.readFloat();
        s.shelf = b.readFloat();
        s.wall = b.readFloat();
        s.hail = b.readFloat();
        s.fade = b.readFloat();
        s.baseY = b.readVarInt();
        s.seed = b.readLong();
        s.tornado = b.readBoolean();
        if (s.tornado) {
            s.torX = b.readFloat();
            s.torZ = b.readFloat();
            s.torY = b.readFloat();
            s.torInt = b.readFloat();
            s.torRad = b.readFloat();
            s.torEf = b.readByte();
        }
        return s;
    }
}
