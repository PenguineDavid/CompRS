package io.github.penguinedavid.comprs.common.mixin;

import io.github.penguinedavid.comprs.common.command.BarrelGiver;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.ItemStackTemplate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ItemStackTemplate.class)
public class OverstackedItemStackTemplateMixin {

    @Inject(method = "validate", at = @At("HEAD"), cancellable = true)
    private void comprs$preserveMarkedOverstack(ItemStack stack, CallbackInfoReturnable<ItemStack> cir) {
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (stack.is(Items.DIAMOND_SWORD) && stack.getCount() > stack.getMaxStackSize()
            && stack.getCount() <= 64 && customData != null
            && customData.copyTag().getBoolean(BarrelGiver.OVERLOADED_STACK_MARKER).orElse(false)) {
            cir.setReturnValue(stack);
        }
    }
}