package dev.cassiopeia.client;

import cyou.rimuru.rimulib.client.api.FilmApi;
import cyou.rimuru.rimulib.client.cinematic.CameraPose;
import cyou.rimuru.rimulib.client.cinematic.Cutscene;
import cyou.rimuru.rimulib.client.cinematic.Shot;
import cyou.rimuru.rimulib.client.cinematic.Subject;
import dev.cassiopeia.CassiopeiaSkill;
import dev.cassiopeia.Plan;
import dev.cassiopeia.Timeline;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * The caster's cutscene, through RimuLib's film director (the same one that plays the Stellar Remote's own films: title
 * card, fade, skip key and the menu's Cutscenes switch all come with it).
 *
 * <p>Seven shots on the {@link Timeline}: a crane up from the horizon while the W is drawn in light; then one shot for
 * every star, the camera low beside it, pushing in as the ground swells, then tilting up to follow the star as it erupts
 * and climbs into the sky; then a pull-back that begins looking up at the finished W in the sky and tilts down to the
 * veins on the land. Every camera path reads the film's clock {@code t} (in timeline ticks), so the shots follow the
 * stars exactly, and the camera shakes with each eruption.
 */
@Environment(EnvType.CLIENT)
final class Film {
    private static final int LEAD = 28;
    private static final int TAIL = 18;

    private Film() {
    }

    static void play(Plan plan, Minecraft client) {
        Subject subject = FilmApi.follow(client.player, plan.centre());
        // A camera path is handed (subject, partial tick, eased progress through its shot), not the film's time, so
        // the film's own clock is read back from the cutscene it belongs to (Cutscene.time, in timeline ticks).
        Cutscene[] clock = new Cutscene[1];
        List<Shot> shots = new ArrayList<>();
        shots.add(FilmApi.cut(0, Timeline.impact(0) - LEAD, (who, partial, k) -> sigil(plan, time(clock, partial), k)));
        for (int k = 0; k < Timeline.STARS; k++) {
            int index = k;
            shots.add(FilmApi.cut(Timeline.impact(k) - LEAD, Timeline.impact(k) + TAIL,
                (who, partial, e) -> closeUp(plan, index, time(clock, partial), e)));
        }
        shots.add(FilmApi.cut(Timeline.impact(Timeline.STARS - 1) + TAIL, Timeline.END,
            (who, partial, e) -> finale(plan, time(clock, partial), e)));
        Cutscene film = FilmApi.film(subject, CassiopeiaSkill.INSTANCE.tier(), CassiopeiaSkill.INSTANCE.title(),
            CassiopeiaSkill.INSTANCE.color(), shots).hardCut().speed(plan.speed());
        clock[0] = film;
        FilmApi.play(film);
    }

    /** The film's clock for this frame, in timeline ticks (0 before the cutscene object exists, i.e. on the very first frame). */
    private static float time(Cutscene[] clock, float partial) {
        Cutscene film = clock[0];
        return film == null ? 0.0F : film.time(partial);
    }

    /** Shot 1: a crane. Starts low and far behind the W, so it fills the horizon, and rises over it as it is drawn. */
    private static CameraPose sigil(Plan plan, float t, float k) {
        Vec3 c = plan.centre();
        double w = plan.width();
        Vec3 position = c.add(plan.forward().scale(-(1.2 - 0.35 * k) * w)).add(plan.right().scale(0.45 * w * (1.0 - k)))
            .add(0.0, 3.0 + (0.55 * w + 10.0) * k, 0.0);
        Vec3 look = c.add(0.0, 5.0 * (1.0 - k) - 2.0 * k, 0.0);
        return pose(plan, position, look, 72.0F - 14.0F * k, 3.0F * (1.0F - k), t);
    }

    /**
     * Shots 2 to 6: low beside a star, pushing in slowly while the ground swells under it, then tilting up with it as it
     * erupts and climbs, so the shot ends looking at the star high in the sky.
     */
    private static CameraPose closeUp(Plan plan, int index, float t, float k) {
        int star = Plan.ORDER[index];
        Vec3 ground = plan.stars()[star];
        double radius = plan.radius()[star];
        double distance = (Math.max(radius * 1.6, 12.0) + 12.0) * (1.0 - 0.3 * k);
        double angle = 2.3 + 0.62 * index + 0.5 * k;
        Vec3 around = plan.right().scale(Math.cos(angle)).add(plan.forward().scale(Math.sin(angle)));
        Vec3 position = ground.add(around.scale(distance)).add(0.0, 2.5 + 3.0 * k, 0.0);
        double charge = Mth.clamp((t - Timeline.launch(index)) / (double) Timeline.CHARGE, 0.0, 1.0);
        double rise = Mth.clamp((t - Timeline.impact(index)) / (double) Timeline.RISE, 0.0, 1.0);
        // the aim point sits a little above the swelling ground, then is carried up by the star itself
        Vec3 look = plan.rise(star, rise).add(0.0, (2.0 + 5.0 * charge) * (1.0 - rise), 0.0);
        return pose(plan, position, look, 76.0F - 16.0F * k, 0.0F, t);
    }

    /** Shot 7: starts low under the finished W in the sky, then tilts down to the veins as the camera climbs and backs away. */
    private static CameraPose finale(Plan plan, float t, float k) {
        Vec3 c = plan.centre();
        double w = plan.width();
        Vec3 start = c.add(plan.forward().scale(-0.35 * w)).add(plan.right().scale(0.2 * w)).add(0.0, 3.0, 0.0);
        Vec3 end = c.add(plan.forward().scale(-1.5 * w)).add(plan.right().scale(-0.25 * w)).add(0.0, 0.6 * w + 20.0, 0.0);
        double tilt = Mth.clamp((k - 0.3) / 0.5, 0.0, 1.0);
        tilt = tilt * tilt * (3.0 - 2.0 * tilt);
        Vec3 look = plan.skyCentre().lerp(c.add(0.0, -2.0, 0.0), tilt);
        return pose(plan, start.lerp(end, k), look, (float) (62.0 + 16.0 * k), 0.0F, t);
    }

    /** A pose with the eruptions' shake added: a few tenths of a block of camera shift and a degree or two of roll. */
    private static CameraPose pose(Plan plan, Vec3 position, Vec3 look, float fov, float roll, float t) {
        double shake = shake(plan, t);
        Vec3 shift = new Vec3(Math.sin(t * 2.3), Math.sin(t * 3.1 + 1.0), Math.sin(t * 2.7 + 2.0)).scale(0.28 * shake);
        return new CameraPose(position.add(shift), look, fov, roll + (float) (Math.sin(t * 1.9) * 2.2 * shake));
    }

    /** Each eruption kicks the camera and the kick dies away in about half a second; bigger craters kick harder. */
    private static double shake(Plan plan, float t) {
        double amount = 0.0;
        for (int k = 0; k < Timeline.STARS; k++) {
            double since = t - Timeline.impact(k);
            if (since >= 0.0) {
                amount += Math.exp(-since * 0.17) * (0.5 + plan.radius()[Plan.ORDER[k]] / 30.0);
            }
        }
        return amount;
    }
}
