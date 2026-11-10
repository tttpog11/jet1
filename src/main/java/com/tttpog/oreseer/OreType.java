package com.tttpog.oreseer;

/** Overworld ores the menu can track. */
public enum OreType {
    COAL("coal", "Coal", 0xFFB0B0B0),
    IRON("iron", "Iron", 0xFFE8C9AE),
    COPPER("copper", "Copper", 0xFFFF8A5C),
    GOLD("gold", "Gold", 0xFFFFE94D),
    REDSTONE("redstone", "Redstone", 0xFFFF3030),
    LAPIS("lapis", "Lapis", 0xFF4F7BFF),
    DIAMOND("diamond", "Diamond", 0xFF4DFFEA),
    EMERALD("emerald", "Emerald", 0xFF22E86A);

    public final String id;
    public final String label;
    public final int color;

    OreType(String id, String label, int color) {
        this.id = id;
        this.label = label;
        this.color = color;
    }

    public String blockPath() {
        return id + "_ore";
    }

    /** Maps a block registry path (e.g. "deepslate_diamond_ore") to an ore type, or null. */
    public static OreType fromBlockPath(String path) {
        if (path.startsWith("deepslate_")) path = path.substring("deepslate_".length());
        for (OreType t : values()) {
            if (t.blockPath().equals(path)) return t;
        }
        return null;
    }

    public static OreType byId(String id) {
        for (OreType t : values()) {
            if (t.id.equals(id)) return t;
        }
        return null;
    }
}
