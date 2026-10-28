package dev.kelphunt;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.lang.reflect.Method;

/**
 * Optional: reads XaeroPlus's Old Chunks / New Chunks data. Done with reflection so this mod
 * has no compile-time dependency on XaeroPlus and keeps working if it is missing or changes.
 * The matching XaeroPlus modules must be switched on for them to have any data.
 */
public final class XaeroPlusLink {
    private static boolean initialised;
    private static Object oldChunks, paletteNew, liquidNew;
    private static Method oldIsOld, paletteIsHl, liquidIsHl;

    private XaeroPlusLink() {}

    private static void init() {
        if (initialised) return;
        initialised = true;
        try {
            Class<?> mm = Class.forName("xaeroplus.module.ModuleManager");
            Method get = mm.getMethod("getModule", Class.class);
            Class<?> oc = Class.forName("xaeroplus.module.impl.OldChunks");
            Class<?> pn = Class.forName("xaeroplus.module.impl.PaletteNewChunks");
            Class<?> ln = Class.forName("xaeroplus.module.impl.LiquidNewChunks");
            oldChunks = get.invoke(null, oc);
            paletteNew = get.invoke(null, pn);
            liquidNew = get.invoke(null, ln);
            oldIsOld = oc.getMethod("isOldChunk", int.class, int.class, ResourceKey.class);
            paletteIsHl = pn.getMethod("isHighlighted", int.class, int.class, ResourceKey.class);
            liquidIsHl = ln.getMethod("isHighlighted", int.class, int.class, ResourceKey.class);
            KelpHuntClient.LOGGER.info("XaeroPlus found: old/new chunk data will be used");
        } catch (Throwable t) {
            oldChunks = null;
            KelpHuntClient.LOGGER.warn("XaeroPlus old/new chunk data unavailable", t);
        }
    }

    private static boolean call(Object target, Method m, int x, int z, ResourceKey<Level> dim) {
        if (target == null || m == null) return false;
        try {
            return (Boolean) m.invoke(target, x, z, dim);
        } catch (Throwable t) {
            return false; // cache being written by XaeroPlus, or API changed: treat as unknown
        }
    }

    public static boolean isOld(int cx, int cz, ResourceKey<Level> dim) {
        init();
        return call(oldChunks, oldIsOld, cx, cz, dim);
    }

    public static boolean isNew(int cx, int cz, ResourceKey<Level> dim) {
        init();
        return call(paletteNew, paletteIsHl, cx, cz, dim) || call(liquidNew, liquidIsHl, cx, cz, dim);
    }
}
