package com.backroomsflashlight.client;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.RenderSystem;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * The spotlight's settings as a uniform buffer we fill ourselves every frame (beam direction, range, strength...).
 * A mixin hands this buffer to the post effect in place of the fixed values in the effect file.
 */
public final class SpotUniform {
    private SpotUniform() {}

    public static final String NAME = "SpotConfig";
    private static GpuBuffer buffer;
    private static final ByteBuffer DATA = ByteBuffer.allocateDirect(6 * 16).order(ByteOrder.nativeOrder());
    private static boolean ready;

    // ---- reflection settings: ReflectConfig = Up, Params, Cam (3 x vec4)
    public static final String REFLECT_NAME = "ReflectConfig";
    private static GpuBuffer rbuffer;
    private static final ByteBuffer RDATA = ByteBuffer.allocateDirect(3 * 16).order(ByteOrder.nativeOrder());
    private static boolean rready;

    public static void writeReflect(float[] v) {
        if (rbuffer == null || rbuffer.isClosed()) {
            rbuffer = RenderSystem.getDevice().createBuffer(() -> "backrooms_flashlight reflect config",
                    GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST, 3L * 16L);
        }
        RDATA.clear();
        for (int i = 0; i < 12; i++) RDATA.putFloat(v[i]);
        RDATA.flip();
        RenderSystem.getDevice().createCommandEncoder().writeToBuffer(rbuffer.slice(), RDATA);
        rready = true;
    }

    public static void resetReflect() { rready = false; }

    /** Our live buffer for the uniform with this name, or null when the effect file's own values should be used. */
    public static GpuBufferSlice sliceFor(String name) {
        if (NAME.equals(name)) return slice();
        if (REFLECT_NAME.equals(name)) return rready && rbuffer != null && !rbuffer.isClosed() ? rbuffer.slice() : null;
        return null;
    }

    /** The slice the post effect should use, or null while nothing has been written yet. */
    public static GpuBufferSlice slice() {
        return ready && buffer != null && !buffer.isClosed() ? buffer.slice() : null;
    }

    public static void reset() {
        ready = false;
    }

    /** Six vec4s in the order the shader declares them: Params, Cone, Origin, Dir, Haze, Core. */
    public static void write(float[] v) {
        if (buffer == null || buffer.isClosed()) {
            buffer = RenderSystem.getDevice().createBuffer(() -> "backrooms_flashlight spot config",
                    GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST, 6L * 16L);
        }
        DATA.clear();
        for (int i = 0; i < 24; i++) DATA.putFloat(v[i]);
        DATA.flip();
        RenderSystem.getDevice().createCommandEncoder().writeToBuffer(buffer.slice(), DATA);
        ready = true;
    }
}
