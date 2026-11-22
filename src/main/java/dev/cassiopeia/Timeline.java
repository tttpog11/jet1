package dev.cassiopeia;

/**
 * The strike's schedule, in ticks at speed 1. The server (blocks, damage, sound), the client (sky, particles) and the
 * film all read this one table, so they stay in step. The speed slider scales all of them together.
 *
 * <pre>
 *   0 .. 50      the W is drawn on the ground in light (the sigil)
 *   84, 130 ...  the five stars erupt out of the ground, one every 46 ticks, dimmest first, Schedar (brightest) last
 *   each star    swells under the ground for the 30 ticks before its eruption, then climbs into the sky over 30 ticks
 *                and hangs there; nothing ever falls
 *   286 .. 330   starlight veins run along the W between the craters
 *   378          the end
 * </pre>
 */
public final class Timeline {
    public static final int STARS = 5;
    public static final int SIGIL_END = 50;
    public static final int FIRST_IMPACT = 84;
    public static final int GAP = 46;
    /** Ticks the ground swells and light gathers before a star erupts. */
    public static final int CHARGE = 30;
    /** Ticks a star takes to climb from its crater to its place in the sky. */
    public static final int RISE = 30;
    public static final int VEINS_START = FIRST_IMPACT + GAP * (STARS - 1) + 18;
    public static final int VEINS_LEN = 44;
    public static final int END = VEINS_START + VEINS_LEN + 48;

    private Timeline() {
    }

    /** The tick the {@code k}-th star (in eruption order) breaks out of the ground. */
    public static int impact(int k) {
        return FIRST_IMPACT + GAP * k;
    }

    /** The tick the ground under the {@code k}-th star starts to swell. */
    public static int launch(int k) {
        return impact(k) - CHARGE;
    }
}
