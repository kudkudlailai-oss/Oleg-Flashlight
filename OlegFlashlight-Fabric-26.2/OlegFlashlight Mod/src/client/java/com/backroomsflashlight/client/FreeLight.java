package com.backroomsflashlight.client;

import com.backroomsflashlight.FlashlightMod;
import net.minecraft.client.Minecraft;

/** The "found_footage" style switch: a flashlight with no item, toggled with a key. Client side only. */
public final class FreeLight {
    private FreeLight() {}

    public static boolean on;

    public static void toggle(Minecraft mc) {
        on = !on;
        if (mc.player != null) mc.player.playSound(FlashlightMod.FLASHLIGHT_CLICK, 0.5f, on ? 1.0f : 0.9f);
    }
}
