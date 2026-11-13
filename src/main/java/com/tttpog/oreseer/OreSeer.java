package com.tttpog.oreseer;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

public class OreSeer implements ClientModInitializer {
    public static final String MODID = "oreseer";

    private static KeyMapping menuKey;

    @Override
    public void onInitializeClient() {
        OreConfig.get();
        menuKey = KeyBindingHelper.registerKeyBinding(
                new KeyMapping("key.oreseer.menu", InputConstants.KEY_RSHIFT, KeyMapping.Category.MISC));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (menuKey.consumeClick()) {
                if (client.screen == null) client.setScreen(new MenuScreen());
            }
            SoundCards.tryRegister(client);
            SoundCards.tick(client);
            OreScanner.tick(client);
        });

        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(MODID, "hud"), (graphics, delta) -> {
            Minecraft mc = Minecraft.getInstance();
            OreHud.render(graphics, mc, delta.getGameTimeDeltaPartialTick(true));
            SoundCards.render(graphics, mc);
        });
    }
}
