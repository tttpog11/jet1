package com.tttpog.oreseer.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.tttpog.oreseer.SkyboxRenderer;
import net.minecraft.client.renderer.SkyRenderer;
import net.minecraft.world.level.MoonPhase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SkyRenderer.class)
public class SkyRendererMixin {
    @Inject(method = "renderSunMoonAndStars", at = @At("TAIL"))
    private void oreseer$skybox(PoseStack poseStack, float sunAngle, float moonAngle, float starAngle,
                                MoonPhase moonPhase, float rainBrightness, float starBrightness, CallbackInfo ci) {
        SkyboxRenderer.render(poseStack, starAngle, starBrightness);
    }
}
