package io.github.penguinedavid.comprs.common.mixin;

import io.github.penguinedavid.comprs.common.gui.CompRSGuiScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TitleScreen.class)
public class TitleScreenMixin {
    @Inject(method = "init", at = @At("TAIL"))
    private void addCompRSButton(CallbackInfo ci) {
    ((ScreenAccessor) this).comprs$addRenderableWidget(Button.builder(Component.literal("compRS"), button ->
                Minecraft.getInstance().gui.setScreen(new CompRSGuiScreen("home")))
            .bounds(8, 8, 70, 20)
            .build());
    }
}