package com.tttpog.stormfront.storm;

/**
 * The three supercell flavours. All geometry is expressed as fractions of the storm radius,
 * in a storm-relative frame: u = along the direction of motion, v = to the right of motion.
 */
public enum StormType {
    //        label                    R     precip coreA coreB shield hailMax torF  wallF shelfF rotF  baseDY puFrac pvFrac wallU  wallV
    LP("Low-Precipitation",           190f, 0.55f, 0.42f, 0.20f, 0.00f, 1.0f, 0.30f, 0.45f, 0.25f, 0.60f,  25,  0.18f,  0.00f, -0.04f, 0.10f),
    CLASSIC("Classic",                260f, 0.85f, 0.80f, 0.45f, 0.08f, 2.5f, 1.00f, 1.00f, 0.55f, 1.00f,   0,  0.50f, -0.30f, -0.24f, 0.30f),
    HP("High-Precipitation",          330f, 1.00f, 1.20f, 0.85f, 0.30f, 4.0f, 0.80f, 0.80f, 1.00f, 0.90f, -15,  0.25f, -0.05f, -0.02f, 0.22f);

    public final String label;
    public final float radius;
    public final float precip;
    public final float coreA, coreB;
    public final float shield;
    public final float hailMax;       // largest hail (inches) this type can produce
    public final float tornadoFactor;
    public final float wallFactor;
    public final float shelfFactor;
    public final float rotFactor;
    public final int baseDY;          // cloud base offset vs. configured base height
    public final float puFrac, pvFrac;   // precip core centre
    public final float wallU, wallV;     // wall cloud / mesocyclone position

    StormType(String label, float radius, float precip, float coreA, float coreB, float shield,
              float hailMax, float tornadoFactor, float wallFactor, float shelfFactor, float rotFactor,
              int baseDY, float puFrac, float pvFrac, float wallU, float wallV) {
        this.label = label;
        this.radius = radius;
        this.precip = precip;
        this.coreA = coreA;
        this.coreB = coreB;
        this.shield = shield;
        this.hailMax = hailMax;
        this.tornadoFactor = tornadoFactor;
        this.wallFactor = wallFactor;
        this.shelfFactor = shelfFactor;
        this.rotFactor = rotFactor;
        this.baseDY = baseDY;
        this.puFrac = puFrac;
        this.pvFrac = pvFrac;
        this.wallU = wallU;
        this.wallV = wallV;
    }

    public static StormType byName(String s) {
        for (StormType t : values()) {
            if (t.name().equalsIgnoreCase(s)) return t;
        }
        return null;
    }
}
