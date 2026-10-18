package com.backroomsflashlight.client;

import com.backroomsflashlight.FlashlightConfig;
import com.backroomsflashlight.FlashlightMod;
import com.backroomsflashlight.item.FlashlightItem;
import com.backroomsflashlight.client.albedo.AlbedoState;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The real light: a post effect that reads the depth buffer, lights everything inside the flashlight cone and adds a
 * soft haze in the air. It is on only while you hold a lit flashlight in first person.
 *
 * The beam has movement delay: it trails behind the camera and catches up. A post effect cannot take values every frame,
 * so the mod fills the effect's settings buffer itself every frame (see SpotUniform and the RenderPass mixin).
 */
public final class SpotlightPass {
    private static final Logger LOGGER = LoggerFactory.getLogger("BackroomsFlashlight");
    private static final int MAX_OFFSET = 6;       // degrees, must match the generated files
    private static final float DELAY = 0.10f;      // seconds the beam takes to catch up (bigger = lazier)

    private static boolean active;
    private static boolean failed;
    private static String current = "";
    private static boolean init;
    private static float lagYaw, lagPitch;
    private static long lastNanos;

    private SpotlightPass() {}

    public static void register() {
        LevelRenderEvents.START_MAIN.register(context -> update(Minecraft.getInstance()));
        LevelRenderEvents.END_MAIN.register(context -> AlbedoState.endFrame());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> { active = false; init = false; current = ""; FreeLight.on = false; AlbedoState.wanted = false; AlbedoState.endFrame(); });
    }

    private static void update(Minecraft mc) {
        if (failed || mc.player == null || mc.level == null || mc.gameRenderer == null) { AlbedoState.endFrame(); return; }
        LocalPlayer p = mc.player;
        boolean want = FlashlightConfig.INSTANCE.spotlight_pass
                && mc.options.getCameraType().isFirstPerson()
                && (FlashlightConfig.foundFootage() ? FreeLight.on : FlashlightItem.isShining(p));
        float reflStrength = (FlashlightConfig.INSTANCE.spotlight_pass && mc.options.getCameraType().isFirstPerson())
                ? FlashlightConfig.reflectionStrength() : 0f;
        if (reflStrength > 0f && !"always".equalsIgnoreCase(FlashlightConfig.INSTANCE.floor_mode)) {
            reflStrength *= Math.min(1f, mc.level.getRainLevel(1.0F) * 1.5f);   // wet floors: only while it rains
        }
        float waterStrength = (FlashlightConfig.INSTANCE.spotlight_pass && mc.options.getCameraType().isFirstPerson()
                && FlashlightConfig.INSTANCE.water_reflections) ? Math.max(1.0f, reflStrength * 1.4f) : 0f;
        boolean refl = reflStrength > 0f || waterStrength > 0f;
        AlbedoState.wanted = want || refl;
        if (want || refl) AlbedoState.beginFrame(); else AlbedoState.endFrame();
        if (refl) {
            // world "up" in view space only depends on the pitch (degrees, positive = looking down)
            float pr = (float) Math.toRadians(p.getViewXRot(mc.getDeltaTracker().getGameTimeDeltaPartialTick(false)));
            try {
                float rpt = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
                double cx = p.xo + (p.getX() - p.xo) * rpt;
                double cz = p.zo + (p.getZ() - p.zo) * rpt;
                float yawRad = (float) Math.toRadians(p.getViewYRot(rpt));
                float tsec = (float) ((System.nanoTime() / 1000L) % 1000000000L / 1.0e6);
                SpotUniform.writeReflect(new float[] { 0f, (float) Math.cos(pr), (float) -Math.sin(pr), 0f,
                        0.7002f, reflStrength, 40f, waterStrength,
                        (float) (cx % 4096.0), (float) (cz % 4096.0), yawRad, tsec });
            } catch (Exception e) {
                failed = true;
                LOGGER.error("[Oleg Flashlight] could not write the reflection settings, disabling the effects", e);
                return;
            }
        } else {
            SpotUniform.resetReflect();
        }
        if (!want) {
            init = false;
            SpotUniform.reset();
            // no light: bloom alone (if on), otherwise nothing
            String lv = FlashlightConfig.bloomLevel();
            String idle = ((lv != null || refl) && FlashlightConfig.INSTANCE.spotlight_pass)
                    ? "bloom_" + (lv == null ? "off" : lv) : null;
            if (idle == null) { if (active) apply(mc, null); }
            else if (!idle.equals(current)) apply(mc, idle);
            return;
        }

        float pt = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        float yaw = p.getViewYRot(pt);
        float pitch = p.getViewXRot(pt);
        long now = System.nanoTime();
        float dt = init ? Math.min(0.25f, (now - lastNanos) / 1.0e9f) : 0f;
        lastNanos = now;
        if (!init) { lagYaw = yaw; lagPitch = pitch; init = true; }

        // the beam direction eases toward where the camera looks
        float k = 1f - (float) Math.exp(-dt / DELAY);
        lagYaw += wrap(yaw - lagYaw) * k;
        lagPitch += (pitch - lagPitch) * k;
        // offset of the beam inside the view: turning right leaves the beam to the left, looking down leaves it above
        float ox = -wrap(yaw - lagYaw);
        float oy = pitch - lagPitch;
        if (ox > MAX_OFFSET) { ox = MAX_OFFSET; lagYaw = yaw + ox; }
        if (ox < -MAX_OFFSET) { ox = -MAX_OFFSET; lagYaw = yaw + ox; }
        if (oy > MAX_OFFSET) { oy = MAX_OFFSET; lagPitch = pitch - oy; }
        if (oy < -MAX_OFFSET) { oy = -MAX_OFFSET; lagPitch = pitch - oy; }

        // live settings for the post effect (a mixin hands this buffer to the effect in place of the fixed file values)
        FlashlightConfig cfg = FlashlightConfig.INSTANCE;
        float range = Math.max(4f, Math.min(80f, cfg.range));
        float strength = Math.max(0.1f, Math.min(4f, cfg.strength));
        float haze = Math.max(0f, Math.min(4f, cfg.volumetric));
        float dx = (float) Math.tan(Math.toRadians(ox));
        float dy = (float) Math.tan(Math.toRadians(oy));
        float[] v;
        if (FlashlightConfig.foundFootage()) {
            v = new float[] {
                    0.7002f, strength, range, 0f,       // Params
                    0.975f, 0.88f, 0f, 0f,              // Cone (wide, soft): ~28 degrees at the edge
                    0f, 0f, 0f, 0f,                     // Origin: your eyes
                    dx, dy, 1f, 0f,                     // Dir
                    0.010f * haze, 1f, 1024f, 0f,       // Haze (volumetric light)
                    0.9985f, 0.985f, 0.9f, 0.55f };      // Core (narrow, bright): ~10 degrees at the edge + weight of the wide cone
        } else {
            v = new float[] {
                    0.7002f, strength, range, 0f,
                    0.993f, 0.975f, 0f, 0f,
                    0.25f, -0.22f, 0.5f, 0f,            // Origin: the lamp in your hand
                    dx, dy, 1f, 0f,
                    0.012f * haze, 1f, 1024f, 0f,
                    0.99f, 0.98f, 0f, 1f };
        }
        try {
            SpotUniform.write(v);
        } catch (Exception e) {
            failed = true;
            LOGGER.error("[Oleg Flashlight] could not write the spotlight settings, disabling it", e);
            return;
        }
        String lv = FlashlightConfig.bloomLevel();
        String name = lv == null ? "spotlight" : "spotlight_" + lv;
        if (!name.equals(current)) apply(mc, name);
    }

    private static void apply(Minecraft mc, String name) {
        try {
            // the vanilla methods are private; 26.2 calls them setPostEffect / clearPostEffect, 26.3 has the longer names
            if (name != null) {
                var m = find("setPostEffect", "setSpectatedEntityPostEffect", Identifier.class);
                m.invoke(mc.gameRenderer, FlashlightMod.id(name));
                active = true;
                current = name;
            } else {
                var m = find("clearPostEffect", "clearSpectatedEntityPostEffect");
                m.invoke(mc.gameRenderer);
                active = false;
                current = "";
            }
        } catch (Exception e) {
            failed = true;
            LOGGER.error("[Oleg Flashlight] could not switch the spotlight pass, disabling it", e);
        }
    }

    private static java.lang.reflect.Method find(String first, String second, Class<?>... args) throws NoSuchMethodException {
        java.lang.reflect.Method m;
        try {
            m = GameRenderer.class.getDeclaredMethod(first, args);
        } catch (NoSuchMethodException e) {
            m = GameRenderer.class.getDeclaredMethod(second, args);
        }
        m.setAccessible(true);
        return m;
    }

    private static float wrap(float deg) {
        deg %= 360f;
        if (deg > 180f) deg -= 360f;
        if (deg < -180f) deg += 360f;
        return deg;
    }

}
