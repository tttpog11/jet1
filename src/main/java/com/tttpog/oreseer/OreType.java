package com.tttpog.oreseer;

/** Every ore block the menu can track, split into Overworld / Deepslate / Nether groups. */
public enum OreType {
    COAL("coal", "Coal", "coal_ore", Group.OVERWORLD, 0xFFB0B0B0),
    IRON("iron", "Iron", "iron_ore", Group.OVERWORLD, 0xFFE8C9AE),
    COPPER("copper", "Copper", "copper_ore", Group.OVERWORLD, 0xFFFF8A5C),
    GOLD("gold", "Gold", "gold_ore", Group.OVERWORLD, 0xFFFFE94D),
    REDSTONE("redstone", "Redstone", "redstone_ore", Group.OVERWORLD, 0xFFFF3030),
    LAPIS("lapis", "Lapis", "lapis_ore", Group.OVERWORLD, 0xFF4F7BFF),
    DIAMOND("diamond", "Diamond", "diamond_ore", Group.OVERWORLD, 0xFF4DFFEA),
    EMERALD("emerald", "Emerald", "emerald_ore", Group.OVERWORLD, 0xFF22E86A),

    D_COAL("deepslate_coal", "Deepslate Coal", "deepslate_coal_ore", Group.DEEPSLATE, 0xFFB0B0B0),
    D_IRON("deepslate_iron", "Deepslate Iron", "deepslate_iron_ore", Group.DEEPSLATE, 0xFFE8C9AE),
    D_COPPER("deepslate_copper", "Deepslate Copper", "deepslate_copper_ore", Group.DEEPSLATE, 0xFFFF8A5C),
    D_GOLD("deepslate_gold", "Deepslate Gold", "deepslate_gold_ore", Group.DEEPSLATE, 0xFFFFE94D),
    D_REDSTONE("deepslate_redstone", "Deepslate Redstone", "deepslate_redstone_ore", Group.DEEPSLATE, 0xFFFF3030),
    D_LAPIS("deepslate_lapis", "Deepslate Lapis", "deepslate_lapis_ore", Group.DEEPSLATE, 0xFF4F7BFF),
    D_DIAMOND("deepslate_diamond", "Deepslate Diamond", "deepslate_diamond_ore", Group.DEEPSLATE, 0xFF4DFFEA),
    D_EMERALD("deepslate_emerald", "Deepslate Emerald", "deepslate_emerald_ore", Group.DEEPSLATE, 0xFF22E86A),

    QUARTZ("nether_quartz", "Nether Quartz", "nether_quartz_ore", Group.NETHER, 0xFFF2EDE6),
    NETHER_GOLD("nether_gold", "Nether Gold", "nether_gold_ore", Group.NETHER, 0xFFFFD23F),
    DEBRIS("ancient_debris", "Ancient Debris", "ancient_debris", Group.NETHER, 0xFFFF9A5A);

    public enum Group {
        OVERWORLD("Overworld Ores"), DEEPSLATE("Deepslate Ores"), NETHER("Nether Ores");

        public final String label;

        Group(String label) {
            this.label = label;
        }
    }

    public final String id;
    public final String label;
    public final String blockPath;
    public final Group group;
    public final int color;

    OreType(String id, String label, String blockPath, Group group, int color) {
        this.id = id;
        this.label = label;
        this.blockPath = blockPath;
        this.group = group;
        this.color = color;
    }

    /** Exact block registry path (e.g. "deepslate_diamond_ore") to ore type, or null. */
    public static OreType fromBlockPath(String path) {
        for (OreType t : values()) {
            if (t.blockPath.equals(path)) return t;
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
