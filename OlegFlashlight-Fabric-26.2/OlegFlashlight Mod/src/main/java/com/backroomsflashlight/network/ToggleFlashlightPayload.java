package com.backroomsflashlight.network;

import com.backroomsflashlight.FlashlightMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client -> server: "I pressed the flashlight key". Carries no data. */
public final class ToggleFlashlightPayload implements CustomPacketPayload {
    public static final ToggleFlashlightPayload INSTANCE = new ToggleFlashlightPayload();
    public static final Type<ToggleFlashlightPayload> TYPE = new Type<>(FlashlightMod.id("toggle_flashlight"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ToggleFlashlightPayload> CODEC = StreamCodec.unit(INSTANCE);

    private ToggleFlashlightPayload() {}

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
