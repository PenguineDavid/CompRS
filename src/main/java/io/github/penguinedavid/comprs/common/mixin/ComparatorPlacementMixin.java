package io.github.penguinedavid.comprs.common.mixin;

import io.github.penguinedavid.comprs.common.main;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ComparatorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ComparatorMode;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MultiPlayerGameMode.class)
public class ComparatorPlacementMixin {
    private static boolean togglingComparator;

    @Inject(method = "useItemOn", at = @At("RETURN"))
    private void onUseItemOn(LocalPlayer player, InteractionHand hand, BlockHitResult hitResult,
                              CallbackInfoReturnable<InteractionResult> cir) {
        if (!main.autoSubtractComparators || togglingComparator || hand != InteractionHand.MAIN_HAND
            || Minecraft.getInstance().gameMode == null) {
            return;
        }

        BlockPos placedPos = hitResult.getBlockPos().relative(hitResult.getDirection());
        BlockState placedState = player.level().getBlockState(placedPos);
        if (!placedState.is(Blocks.COMPARATOR)
            || placedState.getValue(ComparatorBlock.MODE) == ComparatorMode.SUBTRACT) {
            return;
        }

        togglingComparator = true;
        Minecraft minecraft = Minecraft.getInstance();
        int selectedSlot = player.getInventory().getSelectedSlot();
        ItemStack heldItem = player.getMainHandItem().copy();
        try {
            BlockHitResult comparatorHit = new BlockHitResult(
                Vec3.atCenterOf(placedPos), Direction.UP, placedPos, false);
            player.getInventory().setItem(selectedSlot, ItemStack.EMPTY);
            minecraft.gameMode.handleCreativeModeItemAdd(ItemStack.EMPTY, 36 + selectedSlot);
            minecraft.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, comparatorHit);
        } finally {
            player.getInventory().setItem(selectedSlot, heldItem);
            minecraft.gameMode.handleCreativeModeItemAdd(heldItem, 36 + selectedSlot);
            togglingComparator = false;
        }
    }
}