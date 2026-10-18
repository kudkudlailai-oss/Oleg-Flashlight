package com.backroomsflashlight.client.albedo.mixin;

import com.backroomsflashlight.client.albedo.AlbedoState;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.resource.ResourceHandle;
import com.mojang.blaze3d.textures.GpuTextureView;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;

/** A post-effect input called "Albedo" is fed our second buffer instead of a named render target. */
@Mixin(targets = "net.minecraft.client.renderer.PostPass$TargetInput")
public class PostPassTargetInputMixin {
    @Shadow
    private String samplerName;
    private static boolean logged;

    @Inject(method = "texture", at = @At("HEAD"), cancellable = true)
    private void backrooms$albedo(Map<Identifier, ResourceHandle<RenderTarget>> targets, CallbackInfoReturnable<GpuTextureView> cir) {
        if ("Position".equals(samplerName) && AlbedoState.posView != null && !AlbedoState.failed) {
            cir.setReturnValue(AlbedoState.posView);
            return;
        }
        if ("Albedo".equals(samplerName) && AlbedoState.view != null && !AlbedoState.failed) {
            if (!logged) { logged = true; AlbedoState.log("post effect is reading the albedo buffer"); }
            cir.setReturnValue(AlbedoState.view);
        }
    }
}
