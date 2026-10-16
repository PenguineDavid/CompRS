package io.github.penguinedavid.comprs.common.mixin;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Catches mixin wiring mistakes at "gradlew test" time instead of at game launch.
 *
 * Mixin resolves accessor and shadow targets by string at class-load time, so javac
 * cannot see a stale field name. Prefer public API calls (checked by javac) over
 * @Accessor/@Shadow on private fields, and keep the config and sources in sync.
 */
class MixinTargetsTest {

    private static final Path MIXIN_SRC =
        Path.of("src/main/java/io/github/penguinedavid/comprs/common/mixin");
    private static final Path MIXIN_CONFIG = Path.of("src/main/resources/comprs.mixins.json");

    /** Pure file check, needs no Minecraft classes. Every mixin source file must be registered and vice versa. */
    @Test
    void mixinConfigMatchesMixinSourceFiles() throws IOException {
        String json = Files.readString(MIXIN_CONFIG);
        Matcher section = Pattern.compile("\"client\"\\s*:\\s*\\[(.*?)\\]", Pattern.DOTALL).matcher(json);
        assertTrue(section.find(), "comprs.mixins.json has no \"client\" array");

        Set<String> registered = new TreeSet<>();
        Matcher name = Pattern.compile("\"([A-Za-z0-9_$.]+)\"").matcher(section.group(1));
        while (name.find()) {
            registered.add(name.group(1));
        }

        Set<String> onDisk = new TreeSet<>();
        try (Stream<Path> files = Files.list(MIXIN_SRC)) {
            files.map(p -> p.getFileName().toString())
                .filter(n -> n.endsWith(".java"))
                .forEach(n -> onDisk.add(n.substring(0, n.length() - ".java".length())));
        }

        Set<String> notRegistered = new TreeSet<>(onDisk);
        notRegistered.removeAll(registered);
        Set<String> noSourceFile = new TreeSet<>(registered);
        noSourceFile.removeAll(onDisk);

        assertTrue(notRegistered.isEmpty() && noSourceFile.isEmpty(),
            "Source files in the mixin folder but not listed in comprs.mixins.json: " + notRegistered
                + " | Listed in comprs.mixins.json but no source file: " + noSourceFile);
    }
}