package io.github.penguinedavid.comprs.common.mixin;

import io.github.penguinedavid.comprs.common.command.ModCommands; 
import io.github.penguinedavid.comprs.common.main;
import net.minecraft.client.gui.screens.ChatScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChatScreen.class)
public class ChatScreenMixin {
    @Inject(method = "handleChatInput(Ljava/lang/String;Z)V", at = @At("HEAD"), cancellable = true)
    private void onChatSubmit(String message, boolean addToHistory, CallbackInfo ci) {
        if (message.startsWith(",")) {
            String cleanCommand = message.substring(1);
            boolean wasModCommand = ModCommands.executeClientCommand(cleanCommand);

            if (wasModCommand) {
                ci.cancel(); 
            }
        }
    }
}