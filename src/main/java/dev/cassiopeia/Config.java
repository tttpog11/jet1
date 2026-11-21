package dev.cassiopeia;

import java.io.IOException;
import java.io.OutputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import net.fabricmc.loader.api.FabricLoader;

/** config/shooting_star_cassiopeia.properties. Written with its defaults the first time; edit and restart. */
public final class Config {
    private static boolean carveTerrain = true;
    private static int cooldownSeconds = 45;
    private static double maxCraterRadius = 30.0;
    private static double maxPower = 2.5;

    private Config() {
    }

    /** Dig the craters, set the star cores and lay the starlight veins. If false, the strike is light, sound and damage only. */
    public static boolean carveTerrain() {
        return carveTerrain;
    }

    /** The remote's cooldown after a cast, in ticks. */
    public static int cooldownTicks() {
        return cooldownSeconds * 20;
    }

    /** No crater is dug wider than this (in blocks), whatever the power slider says. */
    public static double maxCraterRadius() {
        return maxCraterRadius;
    }

    /** The top of the power slider (1.0 is the normal size: a W 56 blocks wide). */
    public static double maxPower() {
        return maxPower;
    }

    public static void load() {
        Path file = FabricLoader.getInstance().getConfigDir().resolve("shooting_star_cassiopeia.properties");
        Properties props = new Properties();
        if (Files.exists(file)) {
            try (InputStream in = Files.newInputStream(file)) {
                props.load(in);
            } catch (IOException failed) {
                CassiopeiaMod.LOG.warn("Couldn't read {}: {}", file, failed.toString());
            }
        }
        carveTerrain = Boolean.parseBoolean(props.getProperty("carve_terrain", "true"));
        cooldownSeconds = (int) number(props, "cooldown_seconds", 45, 0, 3600);
        maxCraterRadius = number(props, "max_crater_radius", 30, 3, 64);
        maxPower = number(props, "max_power", 2.5, 0.5, 6);
        props.setProperty("carve_terrain", Boolean.toString(carveTerrain));
        props.setProperty("cooldown_seconds", Integer.toString(cooldownSeconds));
        props.setProperty("max_crater_radius", Double.toString(maxCraterRadius));
        props.setProperty("max_power", Double.toString(maxPower));
        try (OutputStream out = Files.newOutputStream(file)) {
            props.store(out, "Cassiopeia. carve_terrain: dig craters, cores and veins (false = light, sound and damage only). "
                + "cooldown_seconds: the remote's cooldown. max_crater_radius: widest crater, in blocks. "
                + "max_power: top of the power slider (1.0 = a W 56 blocks wide).");
        } catch (IOException failed) {
            CassiopeiaMod.LOG.warn("Couldn't write {}: {}", file, failed.toString());
        }
    }

    private static double number(Properties props, String key, double fallback, double min, double max) {
        double value = fallback;
        try {
            value = Double.parseDouble(props.getProperty(key, Double.toString(fallback)).trim());
        } catch (NumberFormatException ignored) {
            // keep the default
        }
        return Math.max(min, Math.min(max, value));
    }
}
