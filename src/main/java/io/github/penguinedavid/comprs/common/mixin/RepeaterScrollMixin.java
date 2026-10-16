package io.github.penguinedavid.comprs.common.mixin;

import io.github.penguinedavid.comprs.common.main;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Items;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public abstract class RepeaterScrollMixin {
    @Shadow
    private Minecraft minecraft;

    @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
    private void setRepeaterDelay(long window, double horizontal, double vertical, CallbackInfo ci) {
        if (!main.autoSetRepeaterDelay || minecraft.player == null || minecraft.gui.screen() != null
            || !minecraft.player.getMainHandItem().is(Items.REPEATER) || vertical == 0
            || !isControlDown(window)) {
            return;
        }

        main.repeaterDelay = Math.max(1, Math.min(4,
            main.repeaterDelay + (vertical > 0 ? 1 : -1)));
        main.saveSettings(minecraft.gameDirectory);
        minecraft.gui.hud.setOverlayMessage(
            Component.literal("Repeater delay: " + main.repeaterDelay), false);
        ci.cancel();
    }

    private boolean isControlDown(long window) {
        return GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS
            || GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS;
    }
}