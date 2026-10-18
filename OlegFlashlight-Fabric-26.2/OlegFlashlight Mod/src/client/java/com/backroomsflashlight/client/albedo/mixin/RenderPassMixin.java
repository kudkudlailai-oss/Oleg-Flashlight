package com.backroomsflashlight.client.albedo.mixin;

import com.backroomsflashlight.client.SpotUniform;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderPass;
import com.backroomsflashlight.client.albedo.AlbedoState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/** Swaps pipelines for three-output copies, but only in passes that have three attachments. */
@Mixin(RenderPass.class)
public class RenderPassMixin {
    @Shadow
    private List<?> colorAttachments;

    @ModifyVariable(method = "setPipeline", at = @At("HEAD"), argsOnly = true)
    private RenderPipeline albedoprobe$swap(RenderPipeline p) {
        if (AlbedoState.inLevel && !AlbedoState.failed && colorAttachments.size() == 3) {
            try {
                return AlbedoState.threeOutput(p);
            } catch (Throwable t) {
                AlbedoState.failed = true;
                t.printStackTrace();
            }
        }
        return p;
    }

    private static boolean backrooms$busy;

    /** The post effect asks for its "SpotConfig" uniform: give it our live one instead. */
    @Inject(method = "setUniform(Ljava/lang/String;Lcom/mojang/blaze3d/buffers/GpuBufferSlice;)V", at = @At("HEAD"), cancellable = true)
    private void backrooms$spotSlice(String name, GpuBufferSlice slice, CallbackInfo ci) {
        GpuBufferSlice mine;
        if (!backrooms$busy && (mine = SpotUniform.sliceFor(name)) != null) {
            backrooms$busy = true;
            try {
                ((RenderPass) (Object) this).setUniform(name, mine);
            } finally {
                backrooms$busy = false;
            }
            ci.cancel();
        }
    }

    @Inject(method = "setUniform(Ljava/lang/String;Lcom/mojang/blaze3d/buffers/GpuBuffer;)V", at = @At("HEAD"), cancellable = true)
    private void backrooms$spotBuffer(String name, GpuBuffer buffer, CallbackInfo ci) {
        GpuBufferSlice mine;
        if (!backrooms$busy && (mine = SpotUniform.sliceFor(name)) != null) {
            backrooms$busy = true;
            try {
                ((RenderPass) (Object) this).setUniform(name, mine);
            } finally {
                backrooms$busy = false;
            }
            ci.cancel();
        }
    }
}
