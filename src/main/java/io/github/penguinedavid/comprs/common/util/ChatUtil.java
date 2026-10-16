package io.github.penguinedavid.comprs.common.util;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

/**
 * Sends chat output for the mod's commands, always prefixed with
 * "[CompRSHelper]" and color-coded by message kind so results are easy to
 * pick out from ordinary chat.
 *
 * success/error take a separate copyValue from the displayed text: displayed
 * text can be descriptive ("5 (base 10) = 101 (base 2)"), while copyValue is
 * just the actual result ("101") that a click-to-copy should grab, not the
 * whole sentence around it. Passing the same string for both is fine when
 * there's no wrapper text to strip.
 */
public final class ChatUtil {

    private ChatUtil() {
    }

    private static final String TAG = "[CompRSHelper]";
    private static final Component COPY_HOVER_TEXT = Component.literal("Click to copy");

    /** Neutral informational output: command results, help text. Not click-to-copy (nothing single to copy). */
    public static void info(String text) {
        send(ChatFormatting.WHITE, text);
    }

    /** Successful computation result. displayText is shown; copyValue is what clicking it copies to clipboard. */
    public static void success(String displayText, String copyValue) {
        sendWithCopy(ChatFormatting.GREEN, displayText, copyValue);
    }

    /** Bad input, parse failures, usage errors. Still click-to-copy, useful for reporting the exact error text. */
    public static void error(String displayText, String copyValue) {
        sendWithCopy(ChatFormatting.RED, displayText, copyValue);
    }

    private static void send(ChatFormatting textColor, String text) {
        if (Minecraft.getInstance().player == null) {
            return;
        }
        MutableComponent message = Component.literal(TAG + " ").withStyle(ChatFormatting.AQUA)
            .append(Component.literal(text).withStyle(textColor));
        Minecraft.getInstance().player.sendSystemMessage(message);
    }

    private static void sendWithCopy(ChatFormatting textColor, String displayText, String copyValue) {
        if (Minecraft.getInstance().player == null) {
            return;
        }
        Style copyStyle = Style.EMPTY
            .withClickEvent(new ClickEvent.CopyToClipboard(copyValue))
            .withHoverEvent(new HoverEvent.ShowText(COPY_HOVER_TEXT));

        MutableComponent message = Component.literal(TAG + " ").withStyle(ChatFormatting.AQUA)
            .append(Component.literal(displayText).withStyle(textColor).withStyle(copyStyle));
        Minecraft.getInstance().player.sendSystemMessage(message);
    }
}