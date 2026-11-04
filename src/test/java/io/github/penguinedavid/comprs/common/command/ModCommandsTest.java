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

    @Test
    void overloadedRejectsWrongArgumentCountWithoutGivingAnything() {
        assertFalse(ModCommands.computeOverloaded(new String[] {}, NEVER_CALLED).success());
        assertFalse(ModCommands.computeOverloaded(new String[] {"543", "544"}, NEVER_CALLED).success());
    }

    @Test
    void overloadedRejectsOutOfRangeAndMalformedSignalsWithoutGivingAnything() {
        for (String bad : new String[] {"15", "898", "-1", "abc", "543.5"}) {
            ModCommands.CommandResult result = ModCommands.computeOverloaded(new String[] {bad}, NEVER_CALLED);
            assertFalse(result.success(), "should reject " + bad);
        }
    }

    @Test
    void overloadedPassesParsedSignalToGiver() {
        int[] seen = {-1};
        IntFunction<BarrelGiver.Result> fake = signal -> {
            seen[0] = signal;
            return new BarrelGiver.Result(true, "Given overloaded signal " + signal + " barrel");
        };

        ModCommands.CommandResult result = ModCommands.computeOverloaded(new String[] {"543"}, fake);

        assertTrue(result.success());
        assertEquals(543, seen[0]);
        assertEquals("Given overloaded signal 543 barrel", result.message());
    }

    @Test
    void overloadedCommandIsDispatchedAndDocumentedUnderBarrel() {
        assertFalse(ModCommands.commandNames().contains("overloaded"));
        assertTrue(ModCommands.commandNames().contains("barrel"));
        ModCommands.GuiCommandResult result = ModCommands.runCommandForGui("overloaded", new String[] {"898"});
        assertFalse(result.success());
        assertTrue(result.message().contains("between 16 and 897"));

        ModCommands.CommandResult detail = ModCommands.computeHelp(new String[] {"barrel"});
        assertTrue(detail.success());
        assertTrue(detail.message().contains(",overloaded <signal>"));
        assertTrue(detail.message().contains("16 to 897"));
        assertTrue(detail.message().contains("Barrel panel"));

        ModCommands.CommandResult commandList = ModCommands.computeHelp(new String[] {});
        assertTrue(commandList.message().contains("barrel - Gives comparator barrels"));
        assertFalse(commandList.message().contains("overloaded -"));
    }
}