package com.backroomsflashlight.server;

import com.backroomsflashlight.FlashlightMod;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.List;

/**
 * The flashlight no longer places light blocks. Worlds that used an older version may still have some left behind
 * (recorded in the save), so they are removed once when the world starts.
 */
public final class LightCleanup {
    private LightCleanup() {}

    public static void register() {
        ServerLifecycleEvents.SERVER_STARTED.register(LightCleanup::onStarted);
    }

    private static void onStarted(MinecraftServer server) {
        for (ServerLevel lvl : server.getAllLevels()) {
            List<Long> all = lvl.getAttached(FlashlightMod.LEFTOVER);
            if (all == null || all.isEmpty()) continue;
            List<Long> water = lvl.getAttached(FlashlightMod.LEFTOVER_WATER);
            if (water == null) water = List.of();
            for (long key : all) {
                BlockPos pos = BlockPos.of(key);
                if (lvl.getBlockState(pos).is(Blocks.LIGHT)) {
                    lvl.setBlock(pos, water.contains(key) ? Blocks.WATER.defaultBlockState() : Blocks.AIR.defaultBlockState(),
                            Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                }
            }
            lvl.setAttached(FlashlightMod.LEFTOVER, List.of());
            lvl.setAttached(FlashlightMod.LEFTOVER_WATER, List.of());
        }
    }
}
