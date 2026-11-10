package com.tttpog.oreseer;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Saved to config/oreseer.json. */
public final class OreConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static OreConfig instance;

    // Ore Finder module
    public boolean finderEnabled = true;
    public long seed = 6608149111735331168L;
    public int radius = 6; // chunks
    public int minY = -64;
    public int maxY = 128;
    public Set<String> ores = new LinkedHashSet<>(List.of("diamond", "deepslate_diamond"));

    // ore group modules
    public boolean overworldEnabled = true;
    public boolean deepslateEnabled = true;
    public boolean netherEnabled = true;

    // Markers module
    public boolean markersEnabled = true;
    public boolean markerDistance = true;
    public int markerMaxDist = 128;
    public int markerSize = 5;

    // Ore List module
    public boolean listEnabled = true;
    public int listSize = 8;

    public static OreConfig get() {
        if (instance == null) instance = load();
        return instance;
    }

    private static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve("oreseer.json");
    }

    private static OreConfig load() {
        try {
            Path p = path();
            if (Files.exists(p)) {
                OreConfig c = GSON.fromJson(Files.readString(p), OreConfig.class);
                if (c != null) {
                    if (c.ores == null) c.ores = new LinkedHashSet<>();
                    c.clamp();
                    return c;
                }
            }
        } catch (Exception e) {
            System.err.println("[OreSeer] could not read config: " + e);
        }
        return new OreConfig();
    }

    public void save() {
        try {
            clamp();
            Files.writeString(path(), GSON.toJson(this));
        } catch (Exception e) {
            System.err.println("[OreSeer] could not save config: " + e);
        }
    }

    private void clamp() {
        radius = Math.max(1, Math.min(24, radius));
        minY = Math.max(-64, Math.min(320, minY));
        maxY = Math.max(-64, Math.min(320, maxY));
        if (minY > maxY) minY = maxY;
        markerMaxDist = Math.max(32, Math.min(512, markerMaxDist));
        markerSize = Math.max(3, Math.min(12, markerSize));
        listSize = Math.max(3, Math.min(15, listSize));
    }

    public boolean groupEnabled(OreType.Group g) {
        return switch (g) {
            case OVERWORLD -> overworldEnabled;
            case DEEPSLATE -> deepslateEnabled;
            case NETHER -> netherEnabled;
        };
    }

    /** Ore types to simulate for the given dimension (nether ores only in the nether, the rest only above). */
    public EnumSet<OreType> wantedTypes(boolean nether) {
        EnumSet<OreType> set = EnumSet.noneOf(OreType.class);
        for (String s : ores) {
            OreType t = OreType.byId(s);
            if (t == null) continue;
            if ((t.group == OreType.Group.NETHER) != nether) continue;
            if (!groupEnabled(t.group)) continue;
            set.add(t);
        }
        return set;
    }

    public boolean isOreOn(OreType t) {
        return ores.contains(t.id);
    }

    public void toggleOre(OreType t) {
        if (!ores.remove(t.id)) ores.add(t.id);
    }
}
