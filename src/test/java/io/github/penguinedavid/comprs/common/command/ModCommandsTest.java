package io.github.penguinedavid.comprs.common.command;

import org.junit.jupiter.api.Test;

import java.util.function.IntFunction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModCommandsTest {
    @Test
    void truthCommandExplainsThatTruthTablesAreGuiOnly() {
        ModCommands.GuiCommandResult result = ModCommands.runCommandForGui("truth", new String[] {"A&B"});

        assertFalse(result.success());
        assertTrue(result.message().contains("GUI-only feature"));
    }

    /** A giver that fails the test if it is ever called, for inputs that must be rejected before giving. */
    private static final IntFunction<BarrelGiver.Result> NEVER_CALLED = signal -> {
        throw new AssertionError("giver must not be called for invalid input, got signal " + signal);
    };

    @Test
    void barrelRejectsWrongArgumentCountWithoutGivingAnything() {
        assertFalse(ModCommands.computeBarrel(new String[] {}, NEVER_CALLED).success());
        assertFalse(ModCommands.computeBarrel(new String[] {"7", "8"}, NEVER_CALLED).success());
    }

    @Test
    void barrelRejectsBadSignalStrengthWithoutGivingAnything() {
        for (String bad : new String[] {"0", "16", "-3", "abc", "7.5"}) {
            ModCommands.CommandResult result = ModCommands.computeBarrel(new String[] {bad}, NEVER_CALLED);
            assertFalse(result.success(), "should reject " + bad);
        }
    }

    @Test
    void barrelPassesTheParsedSignalToTheGiverAndReturnsItsMessage() {
        int[] seen = {-1};
        IntFunction<BarrelGiver.Result> fake = signal -> {
            seen[0] = signal;
            return new BarrelGiver.Result(true, "Given signal " + signal + " barrel");
        };

        ModCommands.CommandResult result = ModCommands.computeBarrel(new String[] {"12"}, fake);

        assertTrue(result.success());
        assertEquals(12, seen[0]);
        assertEquals("Given signal 12 barrel", result.message());
    }

    @Test
    void barrelSurfacesTheGiversFailure() {
        IntFunction<BarrelGiver.Result> notCreative =
            signal -> new BarrelGiver.Result(false, "Creative mode is required");

        ModCommands.CommandResult result = ModCommands.computeBarrel(new String[] {"5"}, notCreative);

        assertFalse(result.success());
        assertEquals("Creative mode is required", result.message());
    }

    @Test
    void barrelIsWiredIntoTheSharedDispatch() {
        // Invalid input is rejected before BarrelGiver is touched, so this runs without a game.
        ModCommands.GuiCommandResult result = ModCommands.runCommandForGui("barrel", new String[] {"99"});

        assertFalse(result.success());
        assertTrue(result.message().contains("between 1 and 15"));
    }

    @Test
    void barrelAppearsInHelp() {
        assertTrue(ModCommands.commandNames().contains("barrel"));
        assertTrue(ModCommands.usageFor("barrel").startsWith(",barrel"));

        ModCommands.CommandResult list = ModCommands.computeHelp(new String[] {});
        assertTrue(list.message().contains("barrel"));

        ModCommands.CommandResult detail = ModCommands.computeHelp(new String[] {"barrel"});
        assertTrue(detail.success());
        assertTrue(detail.message().contains("Usage: ,barrel <signal strength>"));
    }
}