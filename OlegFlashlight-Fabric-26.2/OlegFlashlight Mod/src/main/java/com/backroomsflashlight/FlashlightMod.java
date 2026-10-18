package com.backroomsflashlight;

import com.backroomsflashlight.item.FlashlightItem;
import com.backroomsflashlight.network.ToggleFlashlightPayload;
import com.backroomsflashlight.server.LightCleanup;
import com.backroomsflashlight.server.StarterGift;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Unit;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public class FlashlightMod implements ModInitializer {
    public static final String MOD_ID = "backrooms_flashlight";

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }

    public static final SoundEvent FLASHLIGHT_CLICK = Registry.register(BuiltInRegistries.SOUND_EVENT,
            id("flashlight_click"), SoundEvent.createVariableRangeEvent(id("flashlight_click")));

    /** Present on the stack = switched on (a marker, so the item model can switch on has_component). */
    public static final DataComponentType<Unit> LIT = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, id("lit"),
            DataComponentType.<Unit>builder()
                    .persistent(MapCodec.unitCodec(Unit.INSTANCE))
                    .networkSynchronized(StreamCodec.unit(Unit.INSTANCE))
                    .build());

    /** Player: has the starter flashlight been handed out? Copied on death. */
    public static final AttachmentType<Boolean> GIVEN = AttachmentRegistry.create(id("starter_given"),
            builder -> builder.initializer(() -> false).persistent(Codec.BOOL).copyOnDeath());

    /** Level: light blocks we placed, so a crash cannot leave them in the world. */
    public static final AttachmentType<List<Long>> LEFTOVER = AttachmentRegistry.createPersistent(
            id("leftover_lights"), Codec.LONG.listOf());
    public static final AttachmentType<List<Long>> LEFTOVER_WATER = AttachmentRegistry.createPersistent(
            id("leftover_lights_water"), Codec.LONG.listOf());

    private static final ResourceKey<Item> FLASHLIGHT_KEY = ResourceKey.create(Registries.ITEM, id("flashlight"));
    // No durability and no battery: it never runs out.
    public static final FlashlightItem FLASHLIGHT = Registry.register(BuiltInRegistries.ITEM, FLASHLIGHT_KEY,
            new FlashlightItem(new Item.Properties().setId(FLASHLIGHT_KEY).stacksTo(1)));

    @Override
    public void onInitialize() {
        FlashlightConfig.load();

        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES)
                .register(output -> output.accept(FLASHLIGHT));

        // client -> server: "I pressed the flashlight key" (the server checks that you hold one)
        PayloadTypeRegistry.serverboundPlay().register(ToggleFlashlightPayload.TYPE, ToggleFlashlightPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(ToggleFlashlightPayload.TYPE, (payload, context) -> {
            ItemStack stack = FlashlightItem.held(context.player());
            if (!stack.isEmpty()) FlashlightItem.toggle(context.player(), stack);
        });

        LightCleanup.register();
        StarterGift.register();
    }
}
