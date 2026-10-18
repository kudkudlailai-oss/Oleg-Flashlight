package com.backroomsflashlight.client.albedo.mixin;

import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPassDescriptor;
import com.backroomsflashlight.client.albedo.AlbedoState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.Optional;

/** While the world is drawing into the main picture, give each pass two extra colour attachments. */
@Mixin(CommandEncoder.class)
public class CommandEncoderMixin {
    private static final java.util.Set<String> SEEN = new java.util.HashSet<>();

    @ModifyVariable(method = "createRenderPass(Lcom/mojang/blaze3d/systems/RenderPassDescriptor;)Lcom/mojang/blaze3d/systems/RenderPass;",
            at = @At("HEAD"), argsOnly = true)
    private RenderPassDescriptor albedoprobe$attach(RenderPassDescriptor d) {
        if (AlbedoState.inLevel && !AlbedoState.failed && AlbedoState.posView != null && d.colorAttachments().size() == 1
                && AlbedoState.isMainColor(d.colorAttachments().get(0).textureView())) {
            d.withColorAttachment(AlbedoState.view, Optional.empty());
            d.withColorAttachment(AlbedoState.posView, Optional.empty());
            AlbedoState.log("attached albedo buffer to pass '" + d.label().get() + "'");
        } else if (AlbedoState.inLevel && d.label() != null && SEEN.add(String.valueOf(d.label().get()))) {
            AlbedoState.log("pass '" + d.label().get() + "' left alone (" + d.colorAttachments().size() + " colour attachment(s))");
        }
        return d;
    }
}
