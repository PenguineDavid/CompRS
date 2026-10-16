package io.github.penguinedavid.comprs.common;

import io.github.penguinedavid.comprs.common.command.ModCommands;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public class main implements ModInitializer {
    // init
    public static final String MOD_ID = "comprs";
    public static boolean autoSubtractComparators = false;
    public static boolean autoPlaceSlabsOnTop = false;
    public static boolean autoSetRepeaterDelay = false;
    public static int repeaterDelay = 1;
    private static boolean settingsLoaded;

    private static final String AUTO_SUBTRACT_KEY = "autoSubtractComparators";
    private static final String AUTO_PLACE_SLABS_KEY = "autoPlaceSlabsOnTop";
    private static final String AUTO_SET_REPEATER_DELAY_KEY = "autoSetRepeaterDelay";
    private static final String REPEATER_DELAY_KEY = "repeaterDelay";

    @Override
    public void onInitialize() {
        // Opening a screen directly from inside chat-input handling gets immediately
        // undone by whatever closes the chat screen right after, since that runs in
        // the same tick. Deferring to the next client tick (same fix SeedcrackerX
        // uses for its own gui command) means our screen wins instead.
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (!settingsLoaded) {
                loadSettings(client.gameDirectory);
            }
            ModCommands.tick();
        });
    }

    public static void loadSettings(File gameDirectory) {
        Path settingsPath = gameDirectory.toPath().resolve("config").resolve(MOD_ID).resolve("settings.txt");
        Properties properties = new Properties();
        if (Files.isRegularFile(settingsPath)) {
            try (InputStream input = Files.newInputStream(settingsPath)) {
                properties.load(input);
                autoSubtractComparators = Boolean.parseBoolean(
                    properties.getProperty(AUTO_SUBTRACT_KEY, "false"));
                autoPlaceSlabsOnTop = Boolean.parseBoolean(
                    properties.getProperty(AUTO_PLACE_SLABS_KEY, "false"));
                autoSetRepeaterDelay = Boolean.parseBoolean(
                    properties.getProperty(AUTO_SET_REPEATER_DELAY_KEY, "false"));
                repeaterDelay = clampRepeaterDelay(properties.getProperty(REPEATER_DELAY_KEY, "1"));
            } catch (IOException ignored) {
                autoSubtractComparators = false;
                autoPlaceSlabsOnTop = false;
                autoSetRepeaterDelay = false;
                repeaterDelay = 1;
            }
        }
        settingsLoaded = true;
    }

    public static void saveSettings(File gameDirectory) {
        Path settingsPath = gameDirectory.toPath().resolve("config").resolve(MOD_ID).resolve("settings.txt");
        Properties properties = new Properties();
        properties.setProperty(AUTO_SUBTRACT_KEY, Boolean.toString(autoSubtractComparators));
        properties.setProperty(AUTO_PLACE_SLABS_KEY, Boolean.toString(autoPlaceSlabsOnTop));
        properties.setProperty(AUTO_SET_REPEATER_DELAY_KEY, Boolean.toString(autoSetRepeaterDelay));
        properties.setProperty(REPEATER_DELAY_KEY, Integer.toString(repeaterDelay));
        try {
            Files.createDirectories(settingsPath.getParent());
            try (OutputStream output = Files.newOutputStream(settingsPath)) {
                properties.store(output, "compRS settings");
            }
        } catch (IOException ignored) {
            // Settings remain active for this session even if the file cannot be written.
        }
    }

    public static int clampRepeaterDelay(String value) {
        try {
            return Math.max(1, Math.min(4, Integer.parseInt(value)));
        } catch (NumberFormatException ignored) {
            return 1;
        }
    }
}