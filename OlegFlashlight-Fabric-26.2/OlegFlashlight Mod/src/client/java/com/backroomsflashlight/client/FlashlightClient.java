package com.backroomsflashlight.client;

import com.backroomsflashlight.FlashlightConfig;
import com.backroomsflashlight.FlashlightMod;
import com.backroomsflashlight.item.FlashlightItem;
import com.backroomsflashlight.network.ToggleFlashlightPayload;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.player.LocalPlayer;
import org.lwjgl.glfw.GLFW;

public class FlashlightClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        BeamRenderer.register();
        SpotlightPass.register();
        KeyMapping.Category category = KeyMapping.Category.register(FlashlightMod.id("main"));
        KeyMapping toggle = KeyMappingHelper.registerKeyMapping(
                new KeyMapping("key.backrooms_flashlight.toggle", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_F, category));

        KeyMapping freeToggle = KeyMappingHelper.registerKeyMapping(
                new KeyMapping("key.backrooms_flashlight.toggle_free", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_G, category));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (freeToggle.consumeClick()) {
                // only in the "found_footage" style; the item style ignores this key
                if (FlashlightConfig.foundFootage() && client.player != null) FreeLight.toggle(client);
            }
            LocalPlayer player = client.player;
            while (toggle.consumeClick()) {
                // the key only does something while you are holding a flashlight
                if (!FlashlightConfig.foundFootage() && player != null && !FlashlightItem.held(player).isEmpty()) {
                    ClientPlayNetworking.send(ToggleFlashlightPayload.INSTANCE);
                }
            }
        });
    }
}
