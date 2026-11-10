package com.tttpog.oreseer;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.neoforged.fml.loading.FMLPaths;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
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
    public Set<String> ores = new LinkedHashSet<>(List.of("diamond"));

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
        return FMLPaths.CONFIGDIR.get().resolve("oreseer.json");
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
        markerMaxDist = Math.max(32, Math.min(512, markerMaxDist));
        markerSize = Math.max(3, Math.min(12, markerSize));
        listSize = Math.max(3, Math.min(15, listSize));
    }

    public EnumSet<OreType> wantedTypes() {
        EnumSet<OreType> set = EnumSet.noneOf(OreType.class);
        for (String s : ores) {
            OreType t = OreType.byId(s);
            if (t != null) set.add(t);
        }
        return set;
    }

    public boolean isOreOn(OreType t) {
        return ores.contains(t.id);
    }

    public void toggleOre(OreType t) {
        if (!ores.remove(t.id)) ores.add(t.id);
    }

    /** Anything that changes the simulated result (not the radius). */
    public String signature() {
        List<String> sorted = new ArrayList<>(ores);
        Collections.sort(sorted);
        return seed + "|" + String.join(",", sorted);
    }
}
