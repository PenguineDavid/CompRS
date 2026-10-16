package io.github.penguinedavid.comprs.common.mixin;

import io.github.penguinedavid.comprs.common.main;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MultiPlayerGameMode.class)
public class SlabPlacementMixin {
    private static boolean redirectingSlabPlacement;

    @Inject(method = "useItemOn", at = @At("HEAD"), cancellable = true)
    private void redirectSlabPlacement(LocalPlayer player, InteractionHand hand, BlockHitResult hitResult,
                                       CallbackInfoReturnable<InteractionResult> cir) {
        if (!main.autoPlaceSlabsOnTop || redirectingSlabPlacement || hand != InteractionHand.MAIN_HAND) {
            return;
        }

        ItemStack heldItem = player.getMainHandItem();
        if (!(heldItem.getItem() instanceof BlockItem blockItem)
            || !(blockItem.getBlock() instanceof SlabBlock)) {
            return;
        }

        redirectingSlabPlacement = true;
        try {
            BlockPos clickedPos = hitResult.getBlockPos();
            BlockHitResult upperHalfHit;
            if (hitResult.getDirection() == Direction.UP) {
                BlockPos upperBlock = clickedPos.above();
                upperHalfHit = new BlockHitResult(
                    new Vec3(upperBlock.getX() + 0.5, upperBlock.getY(), upperBlock.getZ() + 0.5),
                    Direction.DOWN, upperBlock, false);
            } else {
                Vec3 originalLocation = hitResult.getLocation();
                upperHalfHit = new BlockHitResult(
                    new Vec3(originalLocation.x, clickedPos.getY() + 0.75, originalLocation.z),
                    hitResult.getDirection(), clickedPos, hitResult.isInside());
            }
            cir.setReturnValue(Minecraft.getInstance().gameMode.useItemOn(player, hand, upperHalfHit));
        } finally {
            redirectingSlabPlacement = false;
        }
    }
}