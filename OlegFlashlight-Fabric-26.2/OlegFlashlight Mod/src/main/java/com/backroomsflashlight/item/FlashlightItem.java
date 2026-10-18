package com.backroomsflashlight.item;

import com.backroomsflashlight.FlashlightMod;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Unit;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

import java.util.function.Consumer;

/** The flashlight. No durability, never needs a battery. On/off is the "lit" component on the stack. */
public class FlashlightItem extends Item {
    public FlashlightItem(Properties properties) {
        super(properties);
    }

    public static boolean isLit(ItemStack stack) {
        return stack.has(FlashlightMod.LIT);
    }

    /** The flashlight in your main hand, else the one in your off hand, else an empty stack. */
    public static ItemStack held(Player player) {
        if (player.getMainHandItem().getItem() instanceof FlashlightItem) return player.getMainHandItem();
        if (player.getOffhandItem().getItem() instanceof FlashlightItem) return player.getOffhandItem();
        return ItemStack.EMPTY;
    }

    /** True while this player is holding a switched-on flashlight. */
    public static boolean isShining(Player player) {
        ItemStack stack = held(player);
        return !stack.isEmpty() && isLit(stack);
    }

    /** Server side: flip the switch and play the click for everyone nearby. */
    public static void toggle(Player player, ItemStack stack) {
        boolean on = !isLit(stack);
        if (on) stack.set(FlashlightMod.LIT, Unit.INSTANCE); else stack.remove(FlashlightMod.LIT);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                FlashlightMod.FLASHLIGHT_CLICK, SoundSource.PLAYERS, 0.5f, on ? 1.0f : 0.9f);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!level.isClientSide()) {
            toggle(player, player.getItemInHand(hand));
        }
        // FAIL: no hand swing and no "item used" push-down animation.
        return InteractionResult.FAIL;
    }

    /** Switching on/off changes a component: do not play the "new item" bounce. */
    @Override
    public boolean allowComponentsUpdateAnimation(Player player, InteractionHand hand, ItemStack oldStack, ItemStack newStack) {
        return false;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> lines, TooltipFlag flag) {
        lines.accept(Component.translatable("tooltip.backrooms_flashlight.infinite").withStyle(ChatFormatting.GRAY));
        lines.accept(Component.translatable("tooltip.backrooms_flashlight.how_to").withStyle(ChatFormatting.DARK_GRAY));
    }
}
