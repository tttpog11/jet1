package com.tttpog.stormfront;

import net.minecraftforge.common.ForgeConfigSpec;

public final class Config {
    public static final ForgeConfigSpec COMMON;
    public static final ForgeConfigSpec CLIENT;

    // ---- common (server) --------------------------------------------------------------
    public static final ForgeConfigSpec.BooleanValue AUTO_SPAWN;
    public static final ForgeConfigSpec.IntValue SPAWN_MINUTES;
    public static final ForgeConfigSpec.IntValue MAX_STORMS;
    public static final ForgeConfigSpec.DoubleValue SPEED;
    public static final ForgeConfigSpec.IntValue CLOUD_BASE_Y;
    public static final ForgeConfigSpec.DoubleValue TORNADO_CHANCE;
    public static final ForgeConfigSpec.DoubleValue TORNADO_STRENGTH_BIAS;
    public static final ForgeConfigSpec.BooleanValue TORNADO_DESTROYS_BLOCKS;
    public static final ForgeConfigSpec.IntValue BLOCKS_PER_TICK;
    public static final ForgeConfigSpec.IntValue MAX_DEBRIS;

    // ---- client -----------------------------------------------------------------------
    public static final ForgeConfigSpec.BooleanValue CLOUDS;
    public static final ForgeConfigSpec.DoubleValue CLOUD_DETAIL;
    public static final ForgeConfigSpec.IntValue MAX_RENDERED_STORMS;
    public static final ForgeConfigSpec.BooleanValue CAMERA_SHAKE;
    public static final ForgeConfigSpec.BooleanValue REPLACE_VANILLA_CLOUDS;
    public static final ForgeConfigSpec.BooleanValue AMBIENT_CLOUDS;
    public static final ForgeConfigSpec.DoubleValue AMBIENT_COVER;

    static {
        ForgeConfigSpec.Builder c = new ForgeConfigSpec.Builder();
        c.push("storms");
        AUTO_SPAWN = c.comment("Spawn storms automatically. Use /stormfront spawn for manual ones.")
                .define("autoSpawn", true);
        SPAWN_MINUTES = c.comment("Average real-time minutes between automatic storm spawns.")
                .defineInRange("spawnIntervalMinutes", 8, 1, 240);
        MAX_STORMS = c.comment("Maximum simultaneous storms.").defineInRange("maxStorms", 2, 1, 6);
        SPEED = c.comment("Storm movement speed multiplier.").defineInRange("speedMultiplier", 1.0, 0.1, 5.0);
        CLOUD_BASE_Y = c.comment("World height of the cloud base.").defineInRange("cloudBaseY", 150, 100, 220);
        c.pop();

        c.push("tornadoes");
        TORNADO_CHANCE = c.comment("Chance (0-1) a mature, rotating storm produces a tornado.")
                .defineInRange("tornadoChance", 0.40, 0.0, 1.0);
        TORNADO_STRENGTH_BIAS = c.comment("1.0 = normal. Higher shifts tornadoes toward EF4/EF5.")
                .defineInRange("strengthBias", 1.25, 0.5, 3.0);
        TORNADO_DESTROYS_BLOCKS = c.comment("Tornadoes destroy blocks. Turn off to make them harmless to terrain.")
                .define("destroyBlocks", true);
        BLOCKS_PER_TICK = c.comment("Max block-removal samples per tick per tornado. LOWER THIS if the server lags.")
                .defineInRange("blocksPerTick", 90, 10, 600);
        MAX_DEBRIS = c.comment("Max flying debris entities at once. LOWER THIS if the game lags.")
                .defineInRange("maxDebris", 100, 0, 600);
        c.pop();
        COMMON = c.build();

        ForgeConfigSpec.Builder k = new ForgeConfigSpec.Builder();
        CLOUDS = k.comment("Render the storm clouds.").define("renderClouds", true);
        CLOUD_DETAIL = k.comment("Cloud puff density. 0.5 = fast, 1.0 = default, 2.0 = pretty.")
                .defineInRange("cloudDetail", 1.0, 0.3, 2.5);
        MAX_RENDERED_STORMS = k.comment("Only the nearest N storms are drawn.")
                .defineInRange("maxRenderedStorms", 2, 1, 5);
        CAMERA_SHAKE = k.define("cameraShake", true);
        REPLACE_VANILLA_CLOUDS = k.comment("Turn off the blocky vanilla clouds.").define("replaceVanillaClouds", true);
        AMBIENT_CLOUDS = k.comment("Draw realistic drifting fair-weather cumulus.").define("ambientClouds", true);
        AMBIENT_COVER = k.comment("Fraction of sky cells that hold a cloud (0.05 = sparse, 0.7 = overcast).")
                .defineInRange("ambientCover", 0.40, 0.05, 0.9);
        CLIENT = k.build();
    }

    private Config() {}
}
