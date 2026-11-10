package com.tttpog.oreseer;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.common.NeoForge;

@Mod(value = OreSeer.MODID, dist = Dist.CLIENT)
public class OreSeer {
    public static final String MODID = "oreseer";

    private static KeyMapping menuKey;

    public OreSeer(IEventBus modBus, ModContainer container) {
        OreConfig.get();
        modBus.addListener(OreSeer::registerKeys);
        NeoForge.EVENT_BUS.addListener(OreSeer::onTick);
        NeoForge.EVENT_BUS.addListener(OreSeer::onGui);
    }

    private static void registerKeys(RegisterKeyMappingsEvent event) {
        menuKey = new KeyMapping("key.oreseer.menu", InputConstants.KEY_RSHIFT, KeyMapping.Category.MISC);
        event.register(menuKey);
    }

    private static void onTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        while (menuKey != null && menuKey.consumeClick()) {
            if (mc.screen == null) mc.setScreen(new MenuScreen());
        }
        OreScanner.tick(mc);
    }

    private static void onGui(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        OreHud.render(event.getGuiGraphics(), mc, mc.getDeltaTracker().getGameTimeDeltaPartialTick(true));
    }
}
