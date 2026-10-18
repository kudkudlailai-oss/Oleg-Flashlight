package com.backroomsflashlight.client.albedo;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.joml.Vector4f;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/** Owns the second ("albedo") buffer and the two-output copies of the terrain pipelines. */
public final class AlbedoState {
    private AlbedoState() {}

    public static final String ID = "backrooms_flashlight";
    /** true from the start to the end of the world's main pass: passes drawing into the main picture get the extra buffers */
    public static boolean inLevel = false;
    /** set by SpotlightPass: the albedo buffer is only drawn while the spotlight is on */
    public static boolean wanted = false;
    public static boolean showDebug = false;
    public static boolean failed = false;

    public static GpuTexture texture;
    public static GpuTextureView view;
    /** view-space position of each terrain pixel (rgb) + 1 in alpha, so the spotlight does not need the depth buffer */
    public static GpuTexture posTexture;
    public static GpuTextureView posView;
    private static int w, h;

    private static final Map<RenderPipeline, RenderPipeline> CLONES = new HashMap<>();
    private static int logged = 0;

    public static void log(String s) {
        if (logged++ < 150) System.out.println("[backrooms_flashlight albedo] " + s);
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(ID, path);
    }

    /** (Re)creates the buffer so it matches the main render target. */
    public static boolean ensure() {
        if (failed || !wanted) return false;
        try {
            RenderTarget main = Minecraft.getInstance().gameRenderer.mainRenderTarget();
            if (main == null) return false;
            if (texture == null || w != main.width || h != main.height) {
                if (view != null) view.close();
                if (texture != null) texture.close();
                if (posView != null) posView.close();
                if (posTexture != null) posTexture.close();
                w = main.width;
                h = main.height;
                texture = RenderSystem.getDevice().createTexture("albedoprobe albedo",
                        GpuTexture.USAGE_RENDER_ATTACHMENT | GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_COPY_SRC | GpuTexture.USAGE_COPY_DST,
                        GpuFormat.RGBA8_UNORM, w, h, 1, 1);
                view = RenderSystem.getDevice().createTextureView(texture);
                posTexture = RenderSystem.getDevice().createTexture("backrooms_flashlight position",
                        GpuTexture.USAGE_RENDER_ATTACHMENT | GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_COPY_SRC | GpuTexture.USAGE_COPY_DST,
                        GpuFormat.RGBA16_FLOAT, w, h, 1, 1);
                posView = RenderSystem.getDevice().createTextureView(posTexture);
                log("created albedo buffer " + w + "x" + h);
            }
            return true;
        } catch (Throwable t) {
            failed = true;
            t.printStackTrace();
            return false;
        }
    }

    /** called at the start of the world pass while the spotlight is on */
    public static void beginFrame() {
        if (ensure()) {
            clear();
            inLevel = !failed;
        } else {
            inLevel = false;
        }
    }

    public static void endFrame() {
        inLevel = false;
    }

    public static boolean isMainColor(com.mojang.blaze3d.textures.GpuTextureView v) {
        try {
            return v == Minecraft.getInstance().gameRenderer.mainRenderTarget().getColorTextureView();
        } catch (Throwable t) {
            return false;
        }
    }

    public static void clear() {
        try {
            var enc = RenderSystem.getDevice().createCommandEncoder();
            enc.clearColorTexture(texture, new Vector4f(0f, 0f, 0f, 0f));
            enc.clearColorTexture(posTexture, new Vector4f(0f, 0f, 0f, 0f));
        } catch (Throwable t) {
            failed = true;
            t.printStackTrace();
        }
    }

    /** vanilla shader -> our copy that also writes unlit colour and position */
    private static final Map<String, String> SWAP = Map.of(
            "minecraft:core/terrain", "core/terrain_albedo",
            "minecraft:core/entity", "core/entity_albedo",
            "minecraft:core/item", "core/item_albedo",
            "minecraft:core/block", "core/block_albedo");

    /**
     * Copy of a vanilla pipeline for a pass that has three colour attachments. Shaders we have a variant for write
     * colour + unlit colour + position. Every other shader gets the same pipeline with the two extra outputs switched
     * off, so it cannot scribble into them.
     */
    public static RenderPipeline threeOutput(RenderPipeline src) {
        RenderPipeline c = CLONES.get(src);
        if (c != null) return c;
        String swap = SWAP.get(src.getVertexShader().toString());
        boolean record = swap != null && swap.equals(SWAP.get(src.getFragmentShader().toString()));
        RenderPipeline.Builder b = RenderPipeline.builder()
                .withLocation(id("pipeline/albedo_" + src.getLocation().getNamespace() + "_" + src.getLocation().getPath().replace('/', '_')))
                .withVertexShader(record ? id(swap) : src.getVertexShader())
                .withFragmentShader(record ? id(swap) : src.getFragmentShader())
                .withPolygonMode(src.getPolygonMode())
                .withCull(src.isCull())
                .withPrimitiveTopology(src.getPrimitiveTopology());
        src.getShaderDefines().flags().forEach(b::withShaderDefine);
        // water, ice and glass are drawn by the translucent terrain pipeline: mark their pixels (position alpha 2)
        if (record && src.getLocation().getPath().contains("translucent")) b.withShaderDefine("TRANSLUCENT_SURFACE");
        src.getShaderDefines().values().forEach((k, v) -> {
            try { b.withShaderDefine(k, Integer.parseInt(v)); }
            catch (NumberFormatException e) { b.withShaderDefine(k, Float.parseFloat(v)); }
        });
        src.getBindGroupLayouts().forEach(b::withBindGroupLayout);
        var vf = src.getVertexFormatBindings();
        for (int i = 0; i < vf.length; i++) if (vf[i] != null) b.withVertexBinding(i, vf[i]);
        b.withDepthStencilState(Optional.ofNullable(src.getDepthStencilState()));
        ColorTargetState[] cts = src.getColorTargetStates();
        b.withColorTargetState(0, cts[0]);
        int mask = record ? ColorTargetState.WRITE_ALL : ColorTargetState.WRITE_NONE;
        b.withColorTargetState(1, new ColorTargetState(Optional.empty(), GpuFormat.RGBA8_UNORM, mask));
        b.withColorTargetState(2, new ColorTargetState(Optional.empty(), GpuFormat.RGBA16_FLOAT, mask));
        c = b.build();
        CLONES.put(src, c);
        log("pipeline " + src.getLocation() + (record ? " -> records albedo+position" : " -> extra outputs off"));
        return c;
    }
}
