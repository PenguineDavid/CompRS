package io.github.penguinedavid.comprs.common.command;

import io.github.penguinedavid.comprs.common.gui.CompRSGuiScreen;
import io.github.penguinedavid.comprs.common.util.BarrelSignal;
import io.github.penguinedavid.comprs.common.util.BaseConverter;
import io.github.penguinedavid.comprs.common.util.BooleanAlgebra;
import io.github.penguinedavid.comprs.common.util.ChatUtil;
import io.github.penguinedavid.comprs.common.util.MathModule;
import io.github.penguinedavid.comprs.common.util.TimeConverter;
import io.github.penguinedavid.comprs.common.util.TwosComplement;
import net.minecraft.client.Minecraft;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntFunction;

/**
 * Client-side chat command handling for compRS.
 *
 * All the actual parsing/computation lives in package-private static methods
 * that return a {@link CommandResult} and never touch Minecraft classes, so
 * they can be unit tested directly. {@link #executeClientCommand} is the only
 * place that talks to {@link ChatUtil}, which is what actually sends chat.
 */
public class ModCommands {

    /**
     * Result of running a command: whether it succeeded, the text to show
     * the player, and copyValue - what a click-to-copy (chat) or Copy button
     * (GUI) should actually put on the clipboard. For most commands the
     * result IS just the value already (eval, simplify, delay, negate), so
     * copyValue defaults to message. binary/conv wrap the value in a
     * descriptive sentence ("5 (base 10) = 101 (base 2)"), so those use the
     * two-argument overload to keep copyValue as just "101", not the whole
     * sentence.
     */
    record CommandResult(boolean success, String message, String copyValue) {
        static CommandResult ok(String message) {
            return new CommandResult(true, message, message);
        }

        static CommandResult ok(String message, String copyValue) {
            return new CommandResult(true, message, copyValue);
        }

        static CommandResult fail(String message) {
            return new CommandResult(false, message, message);
        }
    }

    private record CommandHelp(String usage, String shortDescription, String detailedHelp) {
    }

    private static final Map<String, CommandHelp> COMMAND_HELP = new LinkedHashMap<>();

    static {
        COMMAND_HELP.put("binary", new CommandHelp(
            ",binary <number> | ,binary fixed <intBits.fracBits> <number> | ,binary float [single|double] <number>",
            "Converts a number to its raw binary representation.",
            String.join("\n",
                "Modes:",
                "  ,binary <number>            - auto-detects int or float, prints \"<n> in binary: <bits>\".",
                "  ,binary fixed <i>.<f> <num> - fixed-point i.f layout, prints \"<num> as Qi.f: <bits>\".",
                "  ,binary float <num>         - IEEE 754 single precision, prints \"<num> as float32: <bits>\".",
                "  ,binary float single <num>  - same as above, explicit.",
                "  ,binary float double <num>  - IEEE 754 double precision, prints \"<num> as float64: <bits>\".",
                "Examples:",
                "  ,binary 10",
                "  ,binary fixed 4.4 5",
                "  ,binary float double 3.14")));

        COMMAND_HELP.put("eval", new CommandHelp(
            ",eval <expression>",
            "Evaluates a math expression with parentheses and functions.",
            String.join("\n",
                "Supports + - * / mod, parentheses, pi, e, tau, phi, and the functions sqrt, pow, log, abs, sin, cos, tan.",
                "Use mod as either x mod y or mod(x,y).",
                "Parentheses are validated before evaluation; unbalanced parens are reported as an error.",
                "Trig functions operate in degrees.",
                "Examples:",
                "  ,eval 2 + 3 * 4",
                "  ,eval sqrt(16) + pow(2,5)",
                "  ,eval sin(90)")));

        COMMAND_HELP.put("simplify", new CommandHelp(
            ",simplify <boolean expression>",
            "Finds a minimum sum-of-products form without expanding a shorter expression.",
            String.join("\n",
                "Operators: & or * (AND, also implied by juxtaposition like AB), | or + (OR), ^ (XOR),",
                "! or ~ prefix / ' postfix (NOT). Variables are letters, optionally followed by digits (A, x1).",
                "0 and 1 are literal false/true. Up to 8 distinct variables are supported.",
                "Examples:",
                "  ,simplify A&B | A&!B",
                "  ,simplify (A|B) & (A|!B)",
                "  ,simplify A^B^A",
                "  ,simplify x&b | 1&a")));

        COMMAND_HELP.put("conv", new CommandHelp(
            ",conv <fromBase> <toBase> <number>",
            "Converts a number from one base to another (bases 2-64).",
            String.join("\n",
                "Digits 0-9, then A-Z, then a-z, then + and / give bases up to 64 an",
                "unambiguous digit set (so base 16 uses 0-9A-F, base 36 uses 0-9A-Z, etc.).",
                "For bases up to 36, letters are case-insensitive (ff and FF are the same in",
                "base 16); above base 36, upper and lower case are distinct digit values.",
                "An optional leading '-' is supported for negative numbers.",
                "Digits that aren't valid in <fromBase>, or a base outside 2-64, are reported",
                "as errors instead of silently misconverting.",
                "Examples:",
                "  ,conv 10 2 42",
                "  ,conv 16 10 FF",
                "  ,conv 2 64 1010101")));

        COMMAND_HELP.put("truth", new CommandHelp(
            ",truth",
            "Truth tables are available in the compRS GUI.",
            "Open the compRS GUI and select Truth to view a truth table."));

        COMMAND_HELP.put("delay", new CommandHelp(
            ",delay <number> <fromUnit> <toUnit>",
            "Converts a delay between redstone ticks, game ticks, and milliseconds.",
            String.join("\n",
                "Units: rt (redstone tick, 1/10 s), gt (game tick, 1/20 s), ms (millisecond).",
                "Examples:",
                "  ,delay 5 rt gt",
                "  ,delay 200 ms rt",
                "  ,delay 1 gt ms")));

        COMMAND_HELP.put("negate", new CommandHelp(
            ",negate <bits>",
            "Negates a two's-complement binary value (also works in reverse).",
            String.join("\n",
                "Inverts every bit then adds 1, truncated back to the bit string's own length,",
                "e.g. \"0101\" is treated as 4 bits since that's how many characters it has.",
                "Since this is the same operation both ways, negating a negative value's bit",
                "pattern converts it back to positive.",
                "Examples:",
                "  ,negate 0101",
                "  ,negate 1011")));

        COMMAND_HELP.put("barrel", new CommandHelp(
            ",barrel <signal strength>",
            "Gives you a barrel that makes a comparator read the given signal strength (Creative only).",
            String.join("\n",
                "Signal strength is a whole number from 1 to 15.",
                "The barrel is filled with redstone so a comparator reads exactly that strength,",
                "and is named \"signal <n>\" with an enchantment glint.",
                "It replaces the item in your selected hotbar slot and requires Creative mode.",
                "The Barrel panel in the GUI does the same thing.",
                "Examples:",
                "  ,barrel 7",
                "  ,barrel 15")));

        COMMAND_HELP.put("comprs_gui", new CommandHelp(
            ",compRS_GUI",
            "Opens the compRS GUI (work in progress).",
            String.join("\n",
                "Right now this just opens a blank window, step one toward a full GUI with a",
                "sidebar for jumping between tools (truth table, eval, etc). Every command",
                "still works from chat too; the GUI is an alternative, not a replacement.")));

        COMMAND_HELP.put("help", new CommandHelp(
            ",help [command]",
            "Lists commands, or shows detailed help for one command.",
            String.join("\n",
                "  ,help          - lists every available command with a short description.",
                "  ,help <cmd>    - shows full usage, supported syntax, and examples for <cmd>.")));
    }

    /**
     * Set by ,compRS_GUI, consumed by {@link #tick()}. Opening a screen has to
     * happen on the next client tick rather than immediately: doing it directly
     * from inside chat-input handling gets silently overwritten a moment later
     * by whatever closes the chat screen after a message is "sent".
     */
    private static volatile boolean guiOpenRequested = false;

    /** Called once per client tick (registered in main.java); opens the GUI if requested since the last tick. */
    public static void tick() {
        if (guiOpenRequested) {
            guiOpenRequested = false;
            Minecraft.getInstance().gui.setScreen(new CompRSGuiScreen());
        }
    }

    /** Command names the GUI should offer (everything except opening the GUI itself). */
    public static java.util.List<String> commandNames() {
        java.util.List<String> names = new java.util.ArrayList<>(COMMAND_HELP.keySet());
        names.remove("comprs_gui");
        return names;
    }

    public static String usageFor(String commandName) {
        CommandHelp help = COMMAND_HELP.get(commandName);
        return help == null ? "" : help.usage();
    }

    public static String shortDescriptionFor(String commandName) {
        CommandHelp help = COMMAND_HELP.get(commandName);
        return help == null ? "" : help.shortDescription();
    }

    public static String detailedHelpFor(String commandName) {
        CommandHelp help = COMMAND_HELP.get(commandName);
        return help == null ? "" : help.detailedHelp();
    }

    /**
     * Result of running a command, exposed to non-chat callers (the GUI) that
     * can't see the package-private {@link CommandResult}. Same shape, public.
     */
    public record GuiCommandResult(boolean success, String message, String copyValue) {
    }

    /**
     * Runs commandName with rest exactly the way chat does, but returns a
     * public result instead of sending it to chat. Used by the GUI so every
     * panel calls the exact same logic chat commands do, no duplication.
     */
    public static GuiCommandResult runCommandForGui(String commandName, String[] rest) {
        CommandResult result = compute(commandName.toLowerCase(), rest);
        return new GuiCommandResult(result.success(), result.message(), result.copyValue());
    }

    /** Shared dispatch used by both executeClientCommand (chat) and runCommandForGui (the GUI). */
    private static CommandResult compute(String commandName, String[] rest) {
        return switch (commandName) {
            case "binary" -> computeBinary(rest);
            case "conv" -> computeConv(rest);
            case "eval" -> computeEval(rest);
            case "simplify" -> computeSimplify(rest);
            case "truth" -> computeTruth(rest);
            case "delay" -> computeDelay(rest);
            case "negate" -> computeNegate(rest);
            case "barrel" -> computeBarrel(rest, BarrelGiver::give);
            case "help" -> computeHelp(rest);
            default -> CommandResult.fail("Unknown command '" + commandName + "'");
        };
    }

    /**
     * Entry point called from ChatScreenMixin. Returns true if the message was
     * a recognised mod command (so the mixin cancels sending it as real chat),
     * false if it should be sent through as an ordinary chat message.
     */
    public static boolean executeClientCommand(String rawCommand) {
        String[] args = rawCommand.trim().split("\\s+");
        if (args.length == 0 || args[0].isEmpty()) {
            return false;
        }

        String commandName = args[0].toLowerCase();
        String[] rest = java.util.Arrays.copyOfRange(args, 1, args.length);

        if (commandName.equals("comprs_gui")) {
            guiOpenRequested = true;
            return true;
        }

        // "Unknown command" for the chat path means "not one of ours", so it should
        // fall through as ordinary chat rather than showing an error message.
        if (!isKnownCommand(commandName)) {
            return false;
        }

        CommandResult result = compute(commandName, rest);
        if (result.success()) {
            ChatUtil.success(result.message(), result.copyValue());
        } else {
            ChatUtil.error(result.message(), result.copyValue());
        }
        return true;
    }

    private static boolean isKnownCommand(String commandName) {
        return switch (commandName) {
            case "binary", "conv", "eval", "simplify", "truth", "delay", "negate", "barrel", "help" -> true;
            default -> false;
        };
    }

    // -----------------------------------------------------------------
    // binary
    // -----------------------------------------------------------------

    static CommandResult computeBinary(String[] rest) {
        if (rest.length == 0) {
            return CommandResult.fail("Missing number argument. Usage: ,binary <number>");
        }

        String sub = rest[0].toLowerCase();
        try {
            switch (sub) {
                case "fixed": {
                    if (rest.length < 3) {
                        return CommandResult.fail("Usage: ,binary fixed <intBits.fracBits> <number>");
                    }
                    String[] layout = rest[1].split("\\.");
                    int intBits = Integer.parseInt(layout[0]);
                    int fracBits = Integer.parseInt(layout[1]);
                    int totalBits = intBits + fracBits;

                    float rawValue = Float.parseFloat(rest[2]);
                    int fixedPointValue = Math.round(rawValue * (float) Math.pow(2, fracBits));

                    String bits = padBinary(fixedPointValue, totalBits);
                    String label = "Q" + intBits + "." + fracBits;
                    return CommandResult.ok(rest[2] + " as " + label + ": " + bits, bits);
                }
                case "float": {
                    String floatType = (rest.length > 1) ? rest[1].toLowerCase() : "single";
                    switch (floatType) {
                        case "single": {
                            float value = Float.parseFloat(rest[2]);
                            String bits = padBinary(Float.floatToIntBits(value), 32);
                            return CommandResult.ok(rest[2] + " as float32 (IEEE 754 single): " + bits, bits);
                        }
                        case "double": {
                            double value = Double.parseDouble(rest[2]);
                            String bits = padBinary(Double.doubleToLongBits(value), 64);
                            return CommandResult.ok(rest[2] + " as float64 (IEEE 754 double): " + bits, bits);
                        }
                        default: {
                            // rest[1] wasn't "single"/"double": treat it as the number itself.
                            float value = Float.parseFloat(rest[1]);
                            String bits = padBinary(Float.floatToIntBits(value), 32);
                            return CommandResult.ok(rest[1] + " as float32 (IEEE 754 single): " + bits, bits);
                        }
                    }
                }
                default: {
                    // No subcommand: auto-detect int or float from rest[0].
                    int value;
                    try {
                        value = Integer.parseInt(rest[0]);
                    } catch (NumberFormatException e) {
                        value = Math.round(Float.parseFloat(rest[0]));
                    }
                    String bits = Integer.toBinaryString(value);
                    return CommandResult.ok(rest[0] + " in binary: " + bits, bits);
                }
            }
        } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
            return CommandResult.fail("Invalid layout or number format");
        }
    }

    /**
     * Renders value as binary, zero-padded (or two's-complement-truncated, for
     * negatives) to exactly width bits. Integer.toBinaryString alone never pads,
     * which is why ",binary fixed 4.4 5" used to print "1010000" (7 bits)
     * instead of the expected 8-bit "01010000".
     */
    private static String padBinary(int value, int width) {
        if (width >= 32) {
            return zeroPad(Integer.toBinaryString(value), width);
        }
        int mask = (1 << width) - 1;
        return zeroPad(Integer.toBinaryString(value & mask), width);
    }

    private static String padBinary(long value, int width) {
        if (width >= 64) {
            return zeroPad(Long.toBinaryString(value), width);
        }
        long mask = (1L << width) - 1;
        return zeroPad(Long.toBinaryString(value & mask), width);
    }

    private static String zeroPad(String bits, int width) {
        if (bits.length() >= width) {
            return bits;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = bits.length(); i < width; i++) {
            sb.append('0');
        }
        sb.append(bits);
        return sb.toString();
    }

    // -----------------------------------------------------------------
    // conv
    // -----------------------------------------------------------------

    static CommandResult computeConv(String[] rest) {
        if (rest.length < 3) {
            return CommandResult.fail("Usage: ,conv <fromBase> <toBase> <number>");
        }

        int fromBase;
        int toBase;
        try {
            fromBase = Integer.parseInt(rest[0]);
            toBase = Integer.parseInt(rest[1]);
        } catch (NumberFormatException e) {
            return CommandResult.fail("Bases must be whole numbers between "
                + BaseConverter.MIN_BASE + " and " + BaseConverter.MAX_BASE);
        }

        String number = rest[2];
        try {
            String converted = BaseConverter.convert(fromBase, toBase, number);
            return CommandResult.ok(number + " (base " + fromBase + ") = " + converted + " (base " + toBase + ")", converted);
        } catch (BaseConverter.BaseConversionException e) {
            return CommandResult.fail(e.getMessage());
        }
    }

    // -----------------------------------------------------------------
    // eval
    // -----------------------------------------------------------------

    static CommandResult computeEval(String[] rest) {
        if (rest.length == 0) {
            return CommandResult.fail("Usage: ,eval <expression>");
        }
        String expression = String.join("", rest);

        if (!MathModule.validParentheses(expression)) {
            return CommandResult.fail("Unbalanced parentheses in expression");
        }

        try {
            double result = MathModule.evaluate(expression);
            return CommandResult.ok(formatNumber(result));
        } catch (RuntimeException e) {
            return CommandResult.fail("Could not evaluate expression: " + e.getMessage());
        }
    }

    private static String formatNumber(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return String.valueOf(value);
        }
        if (value == Math.rint(value) && Math.abs(value) < 1e15) {
            return String.valueOf((long) value);
        }
        return String.valueOf(value);
    }

    // -----------------------------------------------------------------
    // simplify
    // -----------------------------------------------------------------

    static CommandResult computeSimplify(String[] rest) {
        if (rest.length == 0) {
            return CommandResult.fail("Usage: ,simplify <boolean expression>");
        }
        String expression = String.join("", rest);

        try {
            String simplified = BooleanAlgebra.simplify(expression);
            return CommandResult.ok("Simplified expression: " + simplified, simplified);
        } catch (BooleanAlgebra.BooleanParseException e) {
            return CommandResult.fail(e.getMessage());
        } catch (RuntimeException e) {
            return CommandResult.fail("Could not simplify expression: " + e.getMessage());
        }
    }

    // -----------------------------------------------------------------
    // truth
    // -----------------------------------------------------------------

    static CommandResult computeTruth(String[] rest) {
        return CommandResult.fail("Truth tables are a GUI-only feature. Open the compRS GUI and select Truth.");
    }

    // -----------------------------------------------------------------
    // delay
    // -----------------------------------------------------------------

    static CommandResult computeDelay(String[] rest) {
        if (rest.length < 3) {
            return CommandResult.fail("Usage: ,delay <number> <fromUnit> <toUnit>");
        }

        double value;
        try {
            value = Double.parseDouble(rest[0]);
        } catch (NumberFormatException e) {
            return CommandResult.fail("'" + rest[0] + "' is not a valid number");
        }

        try {
            double converted = TimeConverter.convert(value, rest[1], rest[2]);
            return CommandResult.ok(formatNumber(converted));
        } catch (TimeConverter.TimeUnitException e) {
            return CommandResult.fail(e.getMessage());
        }
    }

    // -----------------------------------------------------------------
    // negate
    // -----------------------------------------------------------------

    static CommandResult computeNegate(String[] rest) {
        if (rest.length < 1) {
            return CommandResult.fail("Usage: ,negate <bits> (e.g. ,negate 0101)");
        }

        try {
            return CommandResult.ok(TwosComplement.negate(rest[0]));
        } catch (TwosComplement.TwosComplementException e) {
            return CommandResult.fail(e.getMessage());
        }
    }

    // -----------------------------------------------------------------
    // barrel
    // -----------------------------------------------------------------

    /**
     * Validates the signal strength and, only if it is valid, asks {@code giver} to
     * hand the player the barrel. The giver is a parameter so tests can pass a fake
     * instead of needing a running game; production passes {@link BarrelGiver#give}.
     */
    static CommandResult computeBarrel(String[] rest, IntFunction<BarrelGiver.Result> giver) {
        if (rest.length != 1) {
            return CommandResult.fail("Usage: ,barrel <signal strength "
                + BarrelSignal.MIN_SIGNAL + "-" + BarrelSignal.MAX_SIGNAL + ">");
        }

        int signal;
        try {
            signal = BarrelSignal.parse(rest[0]);
        } catch (BarrelSignal.BarrelSignalException e) {
            return CommandResult.fail(e.getMessage());
        }

        BarrelGiver.Result given = giver.apply(signal);
        return given.success() ? CommandResult.ok(given.message()) : CommandResult.fail(given.message());
    }

    // -----------------------------------------------------------------
    // help
    // -----------------------------------------------------------------

    static CommandResult computeHelp(String[] rest) {
        if (rest.length == 0) {
            StringBuilder sb = new StringBuilder("Available commands (use ,help <command> for details):");
            for (Map.Entry<String, CommandHelp> entry : COMMAND_HELP.entrySet()) {
                sb.append("\n  ").append(entry.getKey()).append(" - ").append(entry.getValue().shortDescription());
            }
            return CommandResult.ok(sb.toString());
        }

        String name = rest[0].toLowerCase();
        CommandHelp help = COMMAND_HELP.get(name);
        if (help == null) {
            return CommandResult.fail("Unknown command '" + name + "'. Use ,help to list commands.");
        }
        String text = name.toUpperCase() + "\nUsage: " + help.usage() + "\n" + help.detailedHelp();
        return CommandResult.ok(text);
    }
}