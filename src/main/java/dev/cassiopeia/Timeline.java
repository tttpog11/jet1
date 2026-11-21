package dev.cassiopeia;

/**
 * The strike's schedule, in ticks at speed 1. The server (blocks, damage, sound), the client (sky, particles) and the
 * film all read this one table, so they stay in step. The speed slider scales all of them together.
 *
 * <pre>
 *   0 .. 44      the W is drawn on the ground in light (the sigil)
 *   70, 104 ...  the five stars hit, one every 34 ticks, dimmest first and the brightest star (Schedar) last
 *   each star    falls for the 28 ticks before its impact
 *   222 .. 262   starlight veins run along the W between the craters
 *   298          the end
 * </pre>
 */
public final class Timeline {
    public static final int STARS = 5;
    public static final int SIGIL_END = 44;
    public static final int FIRST_IMPACT = 70;
    public static final int GAP = 34;
    public static final int FALL = 28;
    public static final int VEINS_START = FIRST_IMPACT + GAP * (STARS - 1) + 16;
    public static final int VEINS_LEN = 40;
    public static final int END = VEINS_START + VEINS_LEN + 36;

    private Timeline() {
    }

    /** The tick the {@code k}-th star (in falling order) hits. */
    public static int impact(int k) {
        return FIRST_IMPACT + GAP * k;
    }

    /** The tick the {@code k}-th star starts to fall. */
    public static int launch(int k) {
        return impact(k) - FALL;
    }
}
