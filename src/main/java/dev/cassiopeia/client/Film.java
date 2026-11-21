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
 * <p>Seven shots on the {@link Timeline}: a crane down onto the W while it is drawn in light; then one close shot for
 * every star, the camera low and swinging round it, looking up the length of the fall and down onto the blast; then a
 * long pull-back over the finished constellation. Every camera path reads the film's clock {@code t} (in timeline
 * ticks), so the shots follow the stars exactly, and the camera shakes with each impact.
 */
@Environment(EnvType.CLIENT)
final class Film {
    private static final int LEAD = 26;
    private static final int TAIL = 8;

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

    /** Shot 1: from high behind the caster, craning down onto the W as it is drawn. */
    private static CameraPose sigil(Plan plan, float t, float k) {
        Vec3 c = plan.centre();
        double w = plan.width();
        Vec3 position = c.add(plan.forward().scale(-0.85 * w)).add(plan.right().scale(0.4 * w * (1.0 - k))).add(0.0, 0.5 * w + 16.0 - 0.18 * w * k, 0.0);
        return pose(plan, position, c.add(0.0, -2.0, 0.0), 66.0F - 8.0F * k, 0.0F, t);
    }

    /** Shots 2 to 6: low beside the star that is about to land, looking up its fall, then down into the blast. */
    private static CameraPose closeUp(Plan plan, int index, float t, float k) {
        int star = Plan.ORDER[index];
        Vec3 ground = plan.stars()[star];
        double radius = plan.radius()[star];
        double distance = Math.max(radius * 1.7, 12.0) + 10.0;
        double angle = 2.3 + 0.62 * index + 0.35 * k;
        Vec3 around = plan.right().scale(Math.cos(angle)).add(plan.forward().scale(Math.sin(angle)));
        Vec3 position = ground.add(around.scale(distance)).add(0.0, 4.0 + 8.0 * (1.0 - k), 0.0);
        double fall = Mth.clamp((t - Timeline.launch(index)) / (double) Timeline.FALL, 0.0, 1.0);
        double after = Mth.clamp((t - Timeline.impact(index)) / 8.0, 0.0, 1.0);
        Vec3 look = plan.head(star, fall).add(0.0, 3.0 + 10.0 * after, 0.0);
        return pose(plan, position, look, 74.0F - 10.0F * k, 0.0F, t);
    }

    /** Shot 7: up and back from the last crater, the finished W laid out below and the veins running through it. */
    private static CameraPose finale(Plan plan, float t, float k) {
        Vec3 c = plan.centre();
        double w = plan.width();
        double e = k * k * (3.0 - 2.0 * k);
        Vec3 start = c.add(plan.forward().scale(-0.3 * w)).add(plan.right().scale(0.25 * w)).add(0.0, 7.0, 0.0);
        Vec3 end = c.add(plan.forward().scale(-1.35 * w)).add(plan.right().scale(-0.2 * w)).add(0.0, 0.85 * w + 24.0, 0.0);
        return pose(plan, start.lerp(end, e), c.add(0.0, -4.0 + 10.0 * (1.0 - e), 0.0), (float) (64.0 + 14.0 * e), 0.0F, t);
    }

    /** A pose with the impacts' shake added: a few tenths of a block of camera shift and a degree or two of roll. */
    private static CameraPose pose(Plan plan, Vec3 position, Vec3 look, float fov, float roll, float t) {
        double shake = shake(plan, t);
        Vec3 shift = new Vec3(Math.sin(t * 2.3), Math.sin(t * 3.1 + 1.0), Math.sin(t * 2.7 + 2.0)).scale(0.28 * shake);
        return new CameraPose(position.add(shift), look, fov, roll + (float) (Math.sin(t * 1.9) * 2.2 * shake));
    }

    /** Each impact kicks the camera and the kick dies away in about half a second; bigger craters kick harder. */
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
