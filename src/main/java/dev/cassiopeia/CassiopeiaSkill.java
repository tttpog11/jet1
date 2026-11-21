package dev.cassiopeia;

import cyou.rimuru.rimulib.api.SkillApi;
import cyou.rimuru.rimulib.skill.Power;
import cyou.rimuru.rimulib.skill.Skill;

/**
 * SC-01 Cassiopeia as an entry on the Stellar Remote: its own key, menu card, sliders (power, damage, speed), cooldown
 * and film, like the remote's built-in skills. The menu reads its name, description and key label from the language file
 * (assets/shooting_star_cassiopeia/lang/en_us.json) and its icon from
 * assets/shooting_star_demo/textures/gui/sprites/skill/stellar_remote/cassiopeia.png (named after {@link #ID}).
 */
public final class CassiopeiaSkill implements Skill {
    public static final String ID = "cassiopeia";
    public static final CassiopeiaSkill INSTANCE = new CassiopeiaSkill();

    /** The mod id of The Shooting Star [Demo], and the remote's id path inside it. */
    private static final String TSS_ID = "shooting_star_demo";
    private static final String REMOTE_PATH = "stellar_remote";

    private static boolean installed;

    private CassiopeiaSkill() {
    }

    /** Adds the skill to the Stellar Remote the moment The Shooting Star has registered it. Safe to call twice. */
    public static synchronized void install() {
        if (installed) {
            return;
        }
        installed = true;
        SkillApi.whenRegistered(TSS_ID, set -> {
            if (set.hidden || !REMOTE_PATH.equals(set.id.getPath()) || SkillApi.setOf(INSTANCE) != null) {
                return;
            }
            SkillApi.extend(set, new Skill[]{INSTANCE}, (player, skill, tuning) -> Strike.cast(player, tuning));
            CassiopeiaMod.LOG.info("{} joined the Stellar Remote as skill #{} (key slot {}).", INSTANCE.title(),
                set.skills.indexOf(INSTANCE) + 1, SkillApi.index(INSTANCE));
        });
    }

    @Override
    public String id() {
        return ID;
    }

    /** The platform all the remote's skills belong to (shown on the film's title card). */
    @Override
    public String tier() {
        return "The Shooting Star";
    }

    @Override
    public String title() {
        return "SC-01 · Cassiopeia";
    }

    /** Starlight blue. */
    @Override
    public int color() {
        return 0x7FB2FF;
    }

    @Override
    public int cooldown() {
        return Config.cooldownTicks();
    }

    @Override
    public int duration() {
        return Timeline.END;
    }

    /** GLFW key code 89 = Y: free in vanilla. Players can rebind it in Controls or from the remote's menu. */
    @Override
    public int defaultKey() {
        return 89;
    }

    /** The power slider widens the whole W and every crater. The menu's hint shows the W's width in blocks. */
    @Override
    public Power power() {
        return new Power(0.3F, (float) Config.maxPower(), 1.0F, true, (float) Plan.BASE_WIDTH);
    }

    /** Above this the remote's menu warns that the cast is very heavy. */
    @Override
    public float warnAbove() {
        return 1.8F;
    }

    @Override
    public boolean speedSlider() {
        return true;
    }

    @Override
    public String toString() {
        return title();
    }
}
