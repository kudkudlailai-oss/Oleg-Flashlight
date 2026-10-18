package com.backroomsflashlight.server;

import com.backroomsflashlight.FlashlightConfig;
import com.backroomsflashlight.FlashlightMod;
import com.backroomsflashlight.item.FlashlightItem;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/** Players start with a flashlight (once on first join, and again after dying if they have none). "Spawn with flashlight" off turns off both. */
public final class StarterGift {
    private StarterGift() {}

    public static void register() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayer p = handler.getPlayer();
            if (!FlashlightConfig.INSTANCE.spawn_with_flashlight) return;
            if (Boolean.TRUE.equals(p.getAttached(FlashlightMod.GIVEN))) return;
            p.setAttached(FlashlightMod.GIVEN, true);
            if (!hasOne(p)) give(p);
        });
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
            if (alive || !FlashlightConfig.INSTANCE.spawn_with_flashlight || !FlashlightConfig.INSTANCE.give_on_respawn) return;   // alive = coming back from the End
            if (!hasOne(newPlayer)) give(newPlayer);
        });
    }

    private static boolean hasOne(ServerPlayer p) {
        for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
            if (p.getInventory().getItem(i).getItem() instanceof FlashlightItem) return true;
        }
        return false;
    }

    private static void give(ServerPlayer p) {
        ItemStack stack = new ItemStack(FlashlightMod.FLASHLIGHT);
        if (!p.getInventory().add(stack)) p.drop(stack, false);
    }
}
