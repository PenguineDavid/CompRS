package io.github.penguinedavid.comprs.common.gui;

import io.github.penguinedavid.comprs.common.command.BarrelGiver;
import io.github.penguinedavid.comprs.common.command.ModCommands;
import io.github.penguinedavid.comprs.common.main;
import io.github.penguinedavid.comprs.common.util.BooleanAlgebra;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.net.URI;
import java.net.URISyntaxException;

/**
 * The compRS GUI: a sidebar of buttons down the left (Home plus one per
 * command, plus Kmap), and a main area on the right whose contents depend on
 * which sidebar button was clicked.
 *
 * Design note: rather than dynamically swapping widgets in and out of a
 * single long-lived Screen, each sidebar click just opens a NEW
 * CompRSGuiScreen configured for that panel (Minecraft.getInstance().gui.setScreen(...)
 * from inside a button's onPress, same as vanilla settings screens opening
 * submenus). Field values are saved into a static map before that happens (see
 * SAVED_FIELD_VALUES) and restored when the panel is rebuilt, so switching
 * panels and coming back doesn't lose what you typed. That map is static (not
 * tied to one screen instance) and resets when the game restarts, which is
 * the expected/acceptable scope for "remember what I typed this session".
 *
 * Each command gets its own labeled input fields (see ARG_SPECS) rather than
 * one freeform text box. Whatever gets typed still funnels into the exact
 * same ModCommands methods chat commands use, so GUI and chat can never drift
 * apart in behavior. Kmap is the one exception: it's GUI-only (a 2D grid
 * doesn't have a sensible chat/ASCII form, which was the whole reason this
 * GUI exists in the first place), so it talks to BooleanAlgebra directly
 * instead of going through ModCommands.
 *
 * Scrolling: the sidebar and the main content area (truth/kmap grids) each
 * have their own independent scroll offset, checked by which side of the
 * screen the mouse is over in mouseScrolled(). The sidebar scrolls by
 * repositioning its actual Button widgets; the grids scroll by shifting the Y
 * coordinate used when drawing rows and simply not drawing rows that land
 * outside the visible area (no real clipping/scissor API needed for that).
 *
 * Output is shown in real, read-only EditBox widgets (so it can be
 * highlighted and copied with Ctrl+C like any other text field), not drawn
 * text, plus a Copy button that puts just the actual value on the clipboard
 * (not any descriptive wrapper text around it, e.g. copies "255" rather than
 * "FF (base 16) = 255 (base 10)"). truth/kmap copy a tab-separated text form
 * of the grid instead, since there's no single "the value" for those. Chat
 * results get the same click-to-copy treatment via ChatUtil.
 *
 * Rendering note: this Minecraft version replaced Screen#render(GuiGraphics, ...)
 * with Screen#extractRenderState(GuiGraphicsExtractor, ...), part of a newer
 * split-phase rendering model, and GuiGraphics itself was renamed to
 * GuiGraphicsExtractor. Text is drawn with .text(...) instead of .drawString(...).
 * Colors are packed ARGB ints (0xAARRGGBB) - the alpha byte matters, 0xFFFFFF
 * (no alpha) is fully transparent, not opaque white; it needs to be 0xFFFFFFFF.
 */
public class CompRSGuiScreen extends Screen {

    private static final int SIDEBAR_X = 10;
    private static final int SIDEBAR_WIDTH = 90;
    private static final int SIDEBAR_BUTTON_HEIGHT = 20;
    private static final int SIDEBAR_BUTTON_SPACING = 4;
    private static final int SIDEBAR_TOP = 30;

    private static final int MAIN_X = SIDEBAR_X + SIDEBAR_WIDTH + 20;

    private static final int FIELDS_TOP = 20;
    private static final int FIELD_LABEL_HEIGHT = 11;
    private static final int FIELD_WIDTH = 220;
    private static final int FIELD_HEIGHT = 18;
    private static final int FIELD_GAP = 10;

    private static final int MAX_GUI_VARIABLES = 6;
    private static final int MAX_OUTPUT_LINES = 1;
    private static final int MAX_OUTPUT_LENGTH = 32767;
    private static final int SCROLL_SPEED = 12;

    private static final int COLOR_WHITE = 0xFFFFFFFF;
    private static final int COLOR_GRAY = 0xFFAAAAAA;
    private static final int COLOR_GREEN = 0xFF55FF55;
    private static final int COLOR_RED = 0xFFFF5555;
    private static final int COLOR_LINK = 0xFF55AAFF;

    private static final int TRUE_CELL_COLOR = 0xFF3CB371;  // medium sea green
    private static final int FALSE_CELL_COLOR = 0xFFCD5C5C; // indian red
    private static final int HEADER_CELL_COLOR = 0xFF4A4A4A;
    private static final int CELL_TEXT_COLOR = 0xFFFFFFFF;
    private static final int CELL_WIDTH = 28;
    private static final int CELL_HEIGHT = 18;

    private static final int PANEL_BACKGROUND = 0xE0181820;
    private static final int SIDEBAR_BACKGROUND = 0xE0202030;
    private static final int DIVIDER_COLOR = 0xFF50505F;
    private static final int DIM_BACKGROUND = 0x80000000;

    private static final String BARREL_INTRO =
        "Gives you a barrel with data components to make a comparator read it at the given "
            + "signal strength (Creative mode only)";

    /** One labeled input field: what to call it, and a short hint about what goes in it. */
    private record ArgSpec(String label, String hint) {
    }

    /**
     * Per-panel field layouts for the panels that are still plain labeled text
     * fields. binary and delay aren't here: they use dedicated button-based
     * panels instead (buildBinaryPanel/buildDelayPanel), since their options
     * are small fixed sets (mode, precision, time units) better picked than
     * typed.
     */
    private static final Map<String, List<ArgSpec>> ARG_SPECS = Map.of(
        "conv", List.of(
            new ArgSpec("From base", "2-64"),
            new ArgSpec("To base", "2-64"),
            new ArgSpec("Number", "value written in the from-base")),
        "eval", List.of(
            new ArgSpec("Expression", "e.g. sqrt(16) + pow(2,5)")),
        "simplify", List.of(
            new ArgSpec("Expression", "e.g. A&B | A&!B")),
        "truth", List.of(
            new ArgSpec("Expression", "e.g. x & y (2-" + MAX_GUI_VARIABLES + " variables)")),
        "kmap", List.of(
            new ArgSpec("Expression", "e.g. x & y (2-" + MAX_GUI_VARIABLES + " variables)")),
        "negate", List.of(
            new ArgSpec("Bits", "e.g. 0101 (its length is the width, no need to say 4 bits separately)"))
    );

    /**
     * Field values typed into each panel, kept across panel switches for this
     * game session, keyed by "panel:label" rather than position: binary's
     * field composition changes with its Mode selection (Integer has just
     * Number, Fixed adds Layout), so a positional list could restore a saved
     * value into the wrong field after a mode switch. Keying by label avoids
     * that entirely.
     */
    private static final Map<String, String> SAVED_FIELD_VALUES = new HashMap<>();

    /**
     * Button-group selections (binary's mode/precision, delay's units), keyed
     * by "panel:choiceName". Same idea as SAVED_FIELD_VALUES but for a small
     * fixed set of options picked via buttons instead of typed text.
     */
    private static final Map<String, String> SAVED_CHOICES = new HashMap<>();

    /** "home", "help", "kmap", or a command name from ModCommands.commandNames() (e.g. "truth", "eval"). */
    private final String panel;

    // Command panel state (binary, conv, eval, simplify, delay, negate, truth, kmap)
    private List<ArgSpec> specs = List.of();
    private List<EditBox> fields = new ArrayList<>();
    private List<EditBox> outputBoxes = new ArrayList<>();
    private List<Integer> fieldLabelY = new ArrayList<>();
    private List<String> choiceRowLabels = new ArrayList<>();
    private List<Integer> choiceRowLabelY = new ArrayList<>();
    private int outputTop;
    private int resultLabelY;
    private ModCommands.GuiCommandResult lastResult;

    // Truth panel state
    private BooleanAlgebra.TruthTable truthTable;
    private String truthError;

    // Kmap panel state
    private BooleanAlgebra.TruthTable kmapTable;
    private String kmapError;

    // Help panel state
    private String selectedHelpCommand;

    // Barrel panel state
    private String barrelStatus;
    private boolean barrelSuccess;

    // Scrolling state
    private final List<Button> sidebarButtons = new ArrayList<>();
    private final List<Integer> sidebarBaseY = new ArrayList<>();
    private int sidebarScrollOffset = 0;
    private int contentScrollOffset = 0;
    private int oreAddressY;
    private int oreAddressWidth;
    private boolean oreAddressCopied;

    public CompRSGuiScreen() {
        this("home");
    }

    public CompRSGuiScreen(String panel) {
        super(Component.literal("compRS"));
        this.panel = panel;
    }

    @Override
    protected void init() {
        buildSidebar();

        switch (panel) {
            case "home" -> {
                // nothing to build, renderHome() just draws text
            }
            case "settings" -> {
                buildSettingsPanel();
            }
            case "contact" -> {
                buildContactPanel();
            }
            case "help" -> buildHelpPanel();
            case "truth" -> buildFields(ARG_SPECS.get("truth"), this::runTruth, false);
            case "kmap" -> buildFields(ARG_SPECS.get("kmap"), this::runKmap, false);
            case "binary" -> buildBinaryPanel();
            case "delay" -> buildDelayPanel();
            case "barrel" -> buildBarrelPanel();
            default -> buildFields(
                ARG_SPECS.getOrDefault(panel, List.of(new ArgSpec("Arguments", "space-separated, same as in chat"))),
                this::runGenericCommand, true);
        }
    }

    @Override
    public void onClose() {
        saveCurrentFieldValues();
        super.onClose();
    }

    private void saveCurrentFieldValues() {
        for (int i = 0; i < specs.size(); i++) {
            SAVED_FIELD_VALUES.put(panel + ":" + specs.get(i).label(), fields.get(i).getValue());
        }
    }

    private void buildSidebar() {
        List<String> entries = new ArrayList<>();
        entries.add("home");
        for (String name : ModCommands.commandNames()) {
            if (name.equals("barrel")) {
                continue; // added below, after Kmap, with its own panel
            }
            entries.add(name);
            if (name.equals("truth")) {
                entries.add("kmap"); // group Kmap right next to Truth
            }
        }
        entries.add("barrel");
        entries.add("settings");
        entries.add("contact");

        int y = SIDEBAR_TOP;
        for (String entry : entries) {
            String label = entry.equals("home") ? "Home" : capitalize(entry);
            Button button = Button.builder(Component.literal(label), btn -> {
                    saveCurrentFieldValues();
                    Minecraft.getInstance().gui.setScreen(new CompRSGuiScreen(entry));
                })
                .bounds(SIDEBAR_X, y, SIDEBAR_WIDTH, SIDEBAR_BUTTON_HEIGHT)
                .build();
            button.active = !entry.equals(panel);
            this.addRenderableWidget(button);
            sidebarButtons.add(button);
            sidebarBaseY.add(y);
            y += SIDEBAR_BUTTON_HEIGHT + SIDEBAR_BUTTON_SPACING;
        }
    }

    /** Builds one labeled EditBox per spec (restoring any saved value), then Run/Copy buttons and output boxes. */
    private void buildFields(List<ArgSpec> specs, Runnable onRun, boolean showOutputBoxes) {
        this.specs = specs;
        this.fields = new ArrayList<>();
        this.fieldLabelY = new ArrayList<>();

        int y = FIELDS_TOP;
        for (ArgSpec spec : specs) {
            fieldLabelY.add(y);
            EditBox box = new EditBox(this.font, MAIN_X, y + FIELD_LABEL_HEIGHT, FIELD_WIDTH, FIELD_HEIGHT,
                Component.literal(spec.label()));
            box.setValue(SAVED_FIELD_VALUES.getOrDefault(panel + ":" + spec.label(), ""));
            this.addRenderableWidget(box);
            fields.add(box);
            y += FIELD_LABEL_HEIGHT + FIELD_HEIGHT + FIELD_GAP;
        }

        addRunAndOutputControls(y, onRun, showOutputBoxes);
    }

    /**
     * Adds the Run and Copy buttons below whatever fields/choice rows a panel
     * built, then (for panels with plain-text output, not truth/kmap's grids)
     * a fixed pool of read-only, selectable EditBoxes to actually hold the
     * result text - real text boxes you can highlight and copy from, instead
     * of drawn text floating with nothing to select. The pool is reused
     * across Run presses (see populateOutputBoxes) rather than creating new
     * widgets each time, since there's no confirmed way to remove a widget
     * once added.
     */
    private void addRunAndOutputControls(int y, Runnable onRun, boolean showOutputBoxes) {
        int runY = y + 4;
        this.addRenderableWidget(Button.builder(Component.literal("Run"), btn -> onRun.run())
            .bounds(MAIN_X, runY, 60, 20)
            .build());
        this.addRenderableWidget(Button.builder(Component.literal("Copy"), btn -> copyCurrentOutput())
            .bounds(MAIN_X + 66, runY, 60, 20)
            .build());

        // Previously this computed outputTop first and derived the label position
        // by subtracting from it, which put the label inside the buttons'
        // vertical span (runY to runY+20) since the subtraction didn't account
        // for button height at all. Laying these out top-down instead, each
        // position derived from the one before it, avoids that class of bug.
        int buttonBottom = runY + 20;
        resultLabelY = buttonBottom + 8;
        outputTop = resultLabelY + FIELD_LABEL_HEIGHT + 2;

        outputBoxes = new ArrayList<>();
        if (showOutputBoxes) {
            int width = Math.max(100, this.width - MAIN_X - 20);
            int boxY = outputTop;
            for (int i = 0; i < MAX_OUTPUT_LINES; i++) {
                EditBox box = new EditBox(this.font, MAIN_X, boxY, width, FIELD_HEIGHT, Component.literal("output"));
                box.setMaxLength(MAX_OUTPUT_LENGTH);
                box.setEditable(false);
                this.addRenderableWidget(box);
                outputBoxes.add(box);
                boxY += FIELD_HEIGHT + 2;
            }
        }
    }

    /** Puts text straight into the single output box. EditBox scrolls horizontally on its own if it's too long to fit. */
    private void populateOutputBoxes(String text) {
        if (!outputBoxes.isEmpty()) {
            outputBoxes.get(0).setValue(text);
        }
    }

    /**
     * The Copy button's action, aware of which panel it's on: plain-text
     * panels copy the last result's copyValue (the actual value, not any
     * descriptive wrapper text around it); truth/kmap copy a tab-separated
     * text form of the current grid (or the current error, if there is one
     * instead of a grid yet).
     */
    private void copyCurrentOutput() {
        String text = switch (panel) {
            case "truth" -> truthTable != null ? serializeTruthTable(truthTable) : orEmpty(truthError);
            case "kmap" -> kmapTable != null ? serializeKmapTable(kmapTable) : orEmpty(kmapError);
            default -> lastResult != null ? lastResult.copyValue() : "";
        };
        if (!text.isEmpty()) {
            Minecraft.getInstance().keyboardHandler.setClipboard(text);
        }
    }

    private static String orEmpty(String s) {
        return s == null ? "" : s;
    }

    /** Reads a saved button-group selection for this panel, e.g. getChoice("mode", "") for binary. */
    private String getChoice(String key, String defaultValue) {
        return SAVED_CHOICES.getOrDefault(panel + ":" + key, defaultValue);
    }

    /**
     * Records a button-group selection and rebuilds the panel from scratch so
     * any dependent fields (e.g. binary's Layout box only appearing in Fixed
     * mode) reflect the new choice. Current text field values are saved first
     * so they survive the rebuild.
     */
    private void setChoiceAndRebuild(String key, String value) {
        saveCurrentFieldValues();
        SAVED_CHOICES.put(panel + ":" + key, value);
        Minecraft.getInstance().gui.setScreen(new CompRSGuiScreen(panel));
    }

    /**
     * A row of small buttons, one per option, with the currently-selected one
     * shown in [brackets]. onSelect is called with whichever option was
     * clicked. Returns the Y coordinate the next row should start at.
     */
    private int buildChoiceRow(int y, String label, List<String> options, String selectedOption,
                               java.util.function.Consumer<String> onSelect) {
        choiceRowLabels.add(label);
        choiceRowLabelY.add(y);

        int x = MAIN_X;
        int buttonWidth = 60;
        int spacing = 6;
        for (String option : options) {
            String displayLabel = option.equalsIgnoreCase(selectedOption) ? "[" + option + "]" : option;
            this.addRenderableWidget(Button.builder(Component.literal(displayLabel), btn -> onSelect.accept(option))
                .bounds(x, y + FIELD_LABEL_HEIGHT, buttonWidth, FIELD_HEIGHT)
                .build());
            x += buttonWidth + spacing;
        }
        return y + FIELD_LABEL_HEIGHT + FIELD_HEIGHT + FIELD_GAP;
    }

    /** A single labeled EditBox, added to the same fields/specs lists buildFields() uses. Returns the next row's Y. */
    private int buildTextFieldRow(int y, String label, String hint) {
        specs.add(new ArgSpec(label, hint));
        fieldLabelY.add(y);

        EditBox box = new EditBox(this.font, MAIN_X, y + FIELD_LABEL_HEIGHT, FIELD_WIDTH, FIELD_HEIGHT,
            Component.literal(label));
        box.setValue(SAVED_FIELD_VALUES.getOrDefault(panel + ":" + label, ""));
        this.addRenderableWidget(box);
        fields.add(box);
        return y + FIELD_LABEL_HEIGHT + FIELD_HEIGHT + FIELD_GAP;
    }

    /** Looks up a text field's current value by its label, since binary's field composition varies by mode. */
    private String fieldValueByLabel(String label) {
        for (int i = 0; i < specs.size(); i++) {
            if (specs.get(i).label().equals(label)) {
                return fields.get(i).getValue().trim();
            }
        }
        return "";
    }

    /**
     * binary's Mode (Integer/Fixed/Float) is a button row instead of typed
     * text, since it's a small fixed set. Fixed mode shows a Layout text box
     * (free-form, e.g. "4.4", not a small enough set for buttons); Float mode
     * shows a Single/Double button row instead. Integer mode shows neither,
     * just the Number field.
     */
    private void buildBinaryPanel() {
        this.specs = new ArrayList<>();
        this.fields = new ArrayList<>();
        this.fieldLabelY = new ArrayList<>();
        this.choiceRowLabels = new ArrayList<>();
        this.choiceRowLabelY = new ArrayList<>();

        String mode = getChoice("mode", "");
        int y = FIELDS_TOP;
        y = buildChoiceRow(y, "Mode", List.of("Integer", "Fixed", "Float"),
            mode.isEmpty() ? "Integer" : capitalize(mode),
            selected -> setChoiceAndRebuild("mode", selected.equals("Integer") ? "" : selected.toLowerCase()));

        if (mode.equals("fixed")) {
            y = buildTextFieldRow(y, "Layout", "e.g. 4.4 (intBits.fracBits)");
        } else if (mode.equals("float")) {
            String precision = getChoice("precision", "single");
            y = buildChoiceRow(y, "Precision", List.of("Single", "Double"), capitalize(precision),
                selected -> setChoiceAndRebuild("precision", selected.toLowerCase()));
        }
        y = buildTextFieldRow(y, "Number", "the value to convert");

        addRunAndOutputControls(y, this::runGenericCommand, true);
    }

    /** delay's From/To units (rt/gt/ms) are button rows instead of typed text, since it's a small fixed set. */
    private void buildDelayPanel() {
        this.specs = new ArrayList<>();
        this.fields = new ArrayList<>();
        this.fieldLabelY = new ArrayList<>();
        this.choiceRowLabels = new ArrayList<>();
        this.choiceRowLabelY = new ArrayList<>();

        int y = FIELDS_TOP;
        y = buildTextFieldRow(y, "Number", "the value to convert");

        String fromUnit = getChoice("fromUnit", "rt");
        y = buildChoiceRow(y, "From unit", List.of("Rt", "Gt", "Ms"), capitalize(fromUnit),
            selected -> setChoiceAndRebuild("fromUnit", selected.toLowerCase()));

        String toUnit = getChoice("toUnit", "rt");
        y = buildChoiceRow(y, "To unit", List.of("Rt", "Gt", "Ms"), capitalize(toUnit),
            selected -> setChoiceAndRebuild("toUnit", selected.toLowerCase()));

        addRunAndOutputControls(y, this::runGenericCommand, true);
    }

    /** The barrel panel's description, wrapped to the current window width. */
    private List<String> barrelIntroLines() {
        return wrapText(BARREL_INTRO, this.width - MAIN_X - 20);
    }

    /** Buttons start below however many lines the wrapped description takes, so they never overlap it. */
    private int barrelButtonsTop() {
        return 34 + barrelIntroLines().size() * 11 + 8;
    }

    private void buildBarrelPanel() {
        int buttonWidth = 72;
        int spacing = 6;
        int top = barrelButtonsTop();
        for (int signal = 1; signal <= 15; signal++) {
            int selectedSignal = signal;
            int row = (signal - 1) / 5;
            int column = (signal - 1) % 5;
            int x = MAIN_X + column * (buttonWidth + spacing);
            int y = top + row * 28;
            this.addRenderableWidget(Button.builder(Component.literal("Signal " + signal), btn -> giveBarrel(selectedSignal))
                .bounds(x, y, buttonWidth, FIELD_HEIGHT)
                .build());
        }
    }

    private void buildSettingsPanel() {
        this.addRenderableWidget(Button.builder(
                Component.literal("Auto subtract comparators: "
                    + (main.autoSubtractComparators ? "ON" : "OFF")),
                btn -> {
                    main.autoSubtractComparators = !main.autoSubtractComparators;
                    main.saveSettings(Minecraft.getInstance().gameDirectory);
                    Minecraft.getInstance().gui.setScreen(new CompRSGuiScreen("settings"));
                })
            .bounds(MAIN_X, 50, 190, 20)
            .build());
        this.addRenderableWidget(Button.builder(
                Component.literal("Auto place slabs on top: "
                    + (main.autoPlaceSlabsOnTop ? "ON" : "OFF")),
                btn -> {
                    main.autoPlaceSlabsOnTop = !main.autoPlaceSlabsOnTop;
                    main.saveSettings(Minecraft.getInstance().gameDirectory);
                    Minecraft.getInstance().gui.setScreen(new CompRSGuiScreen("settings"));
                })
            .bounds(MAIN_X, 96, 190, 20)
            .build());
        this.addRenderableWidget(Button.builder(
                Component.literal("Auto set repeater delay: "
                    + (main.autoSetRepeaterDelay ? "ON" : "OFF")),
                btn -> {
                    main.autoSetRepeaterDelay = !main.autoSetRepeaterDelay;
                    main.saveSettings(Minecraft.getInstance().gameDirectory);
                    Minecraft.getInstance().gui.setScreen(new CompRSGuiScreen("settings"));
                })
            .bounds(MAIN_X, 142, 190, 20)
            .build());
    }

    private void buildContactPanel() {
        this.addRenderableWidget(Button.builder(Component.literal("https://penguinedavid.github.io"),
                btn -> openLink("https://penguinedavid.github.io"))
            .bounds(MAIN_X, 58, 250, 20)
            .build());
        this.addRenderableWidget(Button.builder(Component.literal("https://github.com/penguinedavid/compRS"),
                btn -> openLink("https://github.com/penguinedavid/compRS"))
            .bounds(MAIN_X, 99, 250, 20)
            .build());
    }

    private static void openLink(String url) {
        try {
            ConfirmLinkScreen.confirmLinkNow(
                Minecraft.getInstance().gui.screen(), new URI(url));
        } catch (URISyntaxException ignored) {
            // The URLs above are fixed constants and should always be valid.
        }
    }

    private void giveBarrel(int signal) {
        BarrelGiver.Result result = BarrelGiver.give(signal);
        barrelStatus = result.message();
        barrelSuccess = result.success();
    }

    private void runGenericCommand() {
        String[] args = switch (panel) {
            case "binary" -> buildBinaryArgs();
            case "delay" -> buildDelayArgs();
            case "eval", "simplify" -> splitExpression(fields.get(0).getValue());
            default -> fields.stream().map(EditBox::getValue).toArray(String[]::new);
        };
        lastResult = ModCommands.runCommandForGui(panel, args);
        populateOutputBoxes(lastResult.message());
    }

    /**
     * Mode comes from the button row (getChoice), Number/Layout come from
     * whichever text fields are currently present (looked up by label since
     * the field composition changes with mode), reconstructing exactly the
     * argument shape ModCommands.computeBinary expects.
     */
    private String[] buildBinaryArgs() {
        String mode = getChoice("mode", "");
        String number = fieldValueByLabel("Number");

        if (mode.isEmpty()) {
            return new String[] {number};
        }
        if (mode.equals("fixed")) {
            String layout = fieldValueByLabel("Layout");
            return new String[] {"fixed", layout, number};
        }
        String precision = getChoice("precision", "single");
        return new String[] {"float", precision, number};
    }

    private String[] buildDelayArgs() {
        String number = fieldValueByLabel("Number");
        String fromUnit = getChoice("fromUnit", "rt");
        String toUnit = getChoice("toUnit", "rt");
        return new String[] {number, fromUnit, toUnit};
    }

    private static String[] splitExpression(String text) {
        String trimmed = text.trim();
        if (trimmed.isEmpty()) {
            return new String[0];
        }
        return trimmed.split("\\s+");
    }

    private void runTruth() {
        String expression = fields.get(0).getValue();
        truthTable = null;
        truthError = null;
        contentScrollOffset = 0;
        try {
            BooleanAlgebra.TruthTable table = BooleanAlgebra.evaluateTruthTable(expression);
            if (table.variables().size() > MAX_GUI_VARIABLES) {
                truthError = "Truth tables are limited to " + MAX_GUI_VARIABLES + " variables here ("
                    + "this expression has " + table.variables().size() + ")";
            } else {
                truthTable = table;
            }
        } catch (BooleanAlgebra.BooleanParseException e) {
            truthError = e.getMessage();
        } catch (RuntimeException e) {
            truthError = "Could not build truth table: " + e.getMessage();
        }
    }

    /**
     * Splits the variables into two roughly-equal halves (rows get the first
     * half, columns get the second), and lays each axis out in Gray code order
     * so that adjacent cells always differ by exactly one variable, same as a
     * textbook Karnaugh map. This generalizes past the classic 2-4 variable
     * case cleanly: more variables just means a bigger rectangular grid.
     */
    private void runKmap() {
        String expression = fields.get(0).getValue();
        kmapTable = null;
        kmapError = null;
        contentScrollOffset = 0;
        try {
            BooleanAlgebra.TruthTable table = BooleanAlgebra.evaluateTruthTable(expression);
            int n = table.variables().size();
            if (n > MAX_GUI_VARIABLES) {
                kmapError = "K-maps are limited to " + MAX_GUI_VARIABLES + " variables here ("
                    + "this expression has " + n + ")";
            } else if (n < 2) {
                kmapError = "A k-map needs at least 2 variables (this expression has " + n + ")";
            } else {
                kmapTable = table;
            }
        } catch (BooleanAlgebra.BooleanParseException e) {
            kmapError = e.getMessage();
        } catch (RuntimeException e) {
            kmapError = "Could not build k-map: " + e.getMessage();
        }
    }

    private void buildHelpPanel() {
        List<String> names = ModCommands.commandNames();
        int y = 20;
        for (String name : names) {
            String label = capitalize(name);
            this.addRenderableWidget(Button.builder(Component.literal(label), btn ->
                    // Toggle: clicking the already-selected command collapses it again.
                    selectedHelpCommand = name.equals(selectedHelpCommand) ? null : name)
                .bounds(MAIN_X, y, 100, 18)
                .build());
            y += 21;
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        boolean overSidebar = mouseX < SIDEBAR_X + SIDEBAR_WIDTH + 5;
        if (overSidebar) {
            sidebarScrollOffset = clamp(sidebarScrollOffset - (int) (scrollY * SCROLL_SPEED), 0, maxSidebarScroll());
            repositionSidebarButtons();
        } else {
            contentScrollOffset = clamp(contentScrollOffset - (int) (scrollY * SCROLL_SPEED), 0, maxContentScroll());
        }
        return true;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (panel.equals("home") && event.button() == 0
            && event.x() >= MAIN_X && event.x() <= MAIN_X + oreAddressWidth
            && event.y() >= oreAddressY && event.y() < oreAddressY + this.font.lineHeight) {
            Minecraft.getInstance().keyboardHandler.setClipboard("mc.openredstone.org");
            oreAddressCopied = true;
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    private void repositionSidebarButtons() {
        for (int i = 0; i < sidebarButtons.size(); i++) {
            sidebarButtons.get(i).setY(sidebarBaseY.get(i) - sidebarScrollOffset);
        }
    }

    private int maxSidebarScroll() {
        if (sidebarButtons.isEmpty()) {
            return 0;
        }
        int contentHeight = (sidebarBaseY.get(sidebarBaseY.size() - 1) + SIDEBAR_BUTTON_HEIGHT) - SIDEBAR_TOP;
        int visibleHeight = this.height - SIDEBAR_TOP - 10;
        return Math.max(0, contentHeight - visibleHeight);
    }

    private int maxContentScroll() {
        int contentHeight = switch (panel) {
            case "truth" -> truthTable == null ? 0 : (1 << truthTable.variables().size()) * CELL_HEIGHT;
            case "kmap" -> kmapContentHeight();
            default -> 0;
        };
        int visibleHeight = this.height - outputTop - 10;
        return Math.max(0, contentHeight - visibleHeight);
    }

    private int kmapContentHeight() {
        if (kmapTable == null) {
            return 0;
        }
        int n = kmapTable.variables().size();
        int rowBits = (n + 1) / 2;
        return (1 << rowBits) * CELL_HEIGHT + CELL_HEIGHT; // data rows + the header row
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        renderPanelBackground(graphics);
        super.extractRenderState(graphics, mouseX, mouseY, delta);

        String headerText = "compRS";
        int headerCenterX = SIDEBAR_X + SIDEBAR_WIDTH / 2;
        int headerX = headerCenterX - this.font.width(headerText) / 2;
        graphics.text(this.font, headerText, headerX, 10, COLOR_WHITE, true);

        switch (panel) {
            case "home" -> renderHome(graphics);
            case "settings" -> renderSettings(graphics);
            case "contact" -> renderContact(graphics);
            case "help" -> renderHelp(graphics);
            case "truth" -> renderTruth(graphics);
            case "kmap" -> renderKmap(graphics);
            case "barrel" -> renderBarrel(graphics);
            default -> renderGenericResult(graphics);
        }
    }

    /**
     * Dims the game world, then fills the entire screen edge-to-edge with a
     * sidebar panel, a divider, and a main content panel - no gaps between
     * them and no margin around the outside, regardless of window size.
     *
     * All three boundaries are derived from SIDEBAR_X/SIDEBAR_WIDTH alone
     * (not from MAIN_X, which is where the actual field/button content
     * starts, a bit further in for breathing room) specifically so the
     * background regions can't drift out of alignment with each other the
     * way they previously did (a stray few pixels of uncovered background
     * were visible between the sidebar and main panels).
     */
    private void renderPanelBackground(GuiGraphicsExtractor graphics) {
        int sidebarPanelRight = SIDEBAR_X + SIDEBAR_WIDTH + 5;
        int dividerRight = sidebarPanelRight + 2;

        graphics.fill(0, 0, this.width, this.height, DIM_BACKGROUND);
        graphics.fill(0, 0, sidebarPanelRight, this.height, SIDEBAR_BACKGROUND);
        graphics.fill(sidebarPanelRight, 0, dividerRight, this.height, DIVIDER_COLOR);
        graphics.fill(dividerRight, 0, this.width, this.height, PANEL_BACKGROUND);
    }

    private void renderHome(GuiGraphicsExtractor graphics) {
        int maxWidth = this.width - MAIN_X - 20;
        graphics.text(this.font, "Welcome to compRS.", MAIN_X, 20, COLOR_WHITE, false);

        int y = 34;
        for (String line : wrapText(
            "compRS is a mod that aims to improve the experience of computational redstone with commands "
                + "for conversion, evaluation, and more!", maxWidth)) {
            graphics.text(this.font, line, MAIN_X, y, COLOR_GRAY, false);
            y += 11;
        }
        y += 3;
        for (String line : wrapText("Pick a tool from the sidebar to get started or use them in the chat as commands (see help menu).", maxWidth)) {
            graphics.text(this.font, line, MAIN_X, y, COLOR_GRAY, false);
            y += 11;
        }
        y += 11;
        for (String line : wrapText(
            "If you're new to computational redstone, I highly recommend checking out the ORE "
                + "(Open Redstone Engineers) server. It's a place to meet people who enjoy computational "
                + "redstone and get tips from the community.", maxWidth)) {
            graphics.text(this.font, line, MAIN_X, y, COLOR_GRAY, false);
            y += 11;
        }
        y += 2;
        String oreAddress = "IP: mc.openredstone.org";
        oreAddressY = y;
        oreAddressWidth = this.font.width(oreAddress);
        graphics.text(this.font, oreAddress, MAIN_X, y, COLOR_LINK, false);
        graphics.fill(MAIN_X, y + this.font.lineHeight - 1, MAIN_X + oreAddressWidth,
            y + this.font.lineHeight, COLOR_LINK);
        y += 13;
        if (oreAddressCopied) {
            graphics.text(this.font, "Copied to clipboard!", MAIN_X, y, COLOR_GREEN, false);
            y += 11;
        }
        y += 5;
        for (String line : wrapText("Disclaimer: the compRS mod is not affiliated with or sponsored by ORE.", maxWidth)) {
            graphics.text(this.font, line, MAIN_X, y, COLOR_GRAY, false);
            y += 11;
        }
        y += 5;
        for (String line : wrapText(
            "Tip: This mod is for 26.2, but ORE runs 1.21.8. To join and still use this mod, use the official "
                + "ViaFabricPlus mod, which lets you join servers on other versions while keeping your modern mods!",
            maxWidth)) {
            graphics.text(this.font, line, MAIN_X, y, COLOR_GRAY, false);
            y += 11;
        }
        y += 22;
        graphics.text(this.font, "What is computational redstone?", MAIN_X, y, COLOR_WHITE, false);
        y += 14;
        for (String line : wrapText(
            "Computational redstone is about using redstone to build circuits that process information using logic and binary numbers. "
                + "It can be as simple as a couple of pieces of redstone dust or as large as a full computer. "
                + "It's also a great way to learn about real software and hardware because it uses many of the same core concepts. "
                + "In fact, it's how I got into software development myself.", maxWidth)) {
            graphics.text(this.font, line, MAIN_X, y, COLOR_GRAY, false);
            y += 11;
        }
        y += 5;
        graphics.text(this.font, "What compRS is for.", MAIN_X, y, COLOR_WHITE, false);
        y += 14;
        for (String line : wrapText(
            "compRS is not meant to replace the technical knowledge needed to make complex redstone builds. "
                + "It's just a set of tools to speed up the process. "
                + "It won't design the circuit for you; it's here to help you test ideas, check your calculations, and spend more time designing.", maxWidth)) {
            graphics.text(this.font, line, MAIN_X, y, COLOR_GRAY, false);
            y += 11;
        }
    }

    private void renderBarrel(GuiGraphicsExtractor graphics) {
        graphics.text(this.font, "Comparator barrels", MAIN_X, 20, COLOR_WHITE, false);

        int y = 34;
        for (String line : barrelIntroLines()) {
            graphics.text(this.font, line, MAIN_X, y, COLOR_GRAY, false);
            y += 11;
        }

        if (barrelStatus != null) {
            // Three rows of buttons; the status line sits just under the last one.
            int statusY = barrelButtonsTop() + 3 * 28 + 4;
            graphics.text(this.font, barrelStatus, MAIN_X, statusY, barrelSuccess ? COLOR_GREEN : COLOR_RED, false);
        }
    }

    private void renderSettings(GuiGraphicsExtractor graphics) {
        graphics.text(this.font, "Settings", MAIN_X, 20, COLOR_WHITE, false);
        graphics.text(this.font, "Placement and GUI options", MAIN_X, 34, COLOR_GRAY, false);
        graphics.text(this.font, "New comparators are placed in subtract mode.", MAIN_X, 72, COLOR_GRAY, false);
        graphics.text(this.font, "Slabs always use the upper half of the target block.", MAIN_X, 118, COLOR_GRAY, false);
        graphics.text(this.font, "Hold Ctrl and scroll while holding a repeater to set its placement delay (1-4).", MAIN_X, 164, COLOR_GRAY, false);
    }

    private void renderContact(GuiGraphicsExtractor graphics) {
        int maxWidth = this.width - MAIN_X - 20;
        graphics.text(this.font, "Contact", MAIN_X, 20, COLOR_WHITE, false);

        int y = 34;
        for (String line : wrapText("Hi, I'm the developer, and my webpage can be found at:", maxWidth)) {
            graphics.text(this.font, line, MAIN_X, y, COLOR_GRAY, false);
            y += 11;
        }
        y = 83;
        graphics.text(this.font, "GitHub:", MAIN_X, y, COLOR_GRAY, false);

        y += 40;
        graphics.text(this.font, "Licensed under the GPLv3.", MAIN_X, y, COLOR_GRAY, false);
    }

    /** Draws each field's "Label (hint)" line above its EditBox, and each choice row's label above its buttons. */
    private void renderFieldLabels(GuiGraphicsExtractor graphics) {
        for (int i = 0; i < specs.size(); i++) {
            ArgSpec spec = specs.get(i);
            int labelY = fieldLabelY.get(i);
            graphics.text(this.font, spec.label() + " (" + spec.hint() + ")", MAIN_X, labelY, COLOR_GRAY, false);
        }
        for (int i = 0; i < choiceRowLabels.size(); i++) {
            graphics.text(this.font, choiceRowLabels.get(i), MAIN_X, choiceRowLabelY.get(i), COLOR_GRAY, false);
        }
    }

    private void renderGenericResult(GuiGraphicsExtractor graphics) {
        renderFieldLabels(graphics);

        if (panel.equals("eval")) {
            renderEvalFunctions(graphics);
        }

        if (lastResult == null) {
            return;
        }
        // The actual text is in the output EditBoxes now (real, selectable/copyable
        // text boxes), so this just labels them, it doesn't draw the content itself.
        String label = lastResult.success() ? "Result:" : "Error:";
        int color = lastResult.success() ? COLOR_GREEN : COLOR_RED;
        graphics.text(this.font, label, MAIN_X, resultLabelY, color, false);
    }

    private void renderEvalFunctions(GuiGraphicsExtractor graphics) {
        int x = MAIN_X;
        int y = outputTop + FIELD_HEIGHT + 5;
        graphics.text(this.font, "Available functions", x, y, COLOR_WHITE, false);
        String[] functions = {
            "sqrt(x) - square root",
            "pow(x, y) - power",
            "log(x) - base-10 logarithm",
            "abs(x) - absolute value",
            "sin(x), cos(x), tan(x) - degrees",
            "mod(x, y) or x mod y - remainder",
            "Constants: pi, e, tau, phi",
            "Operators: +, -, *, /"
        };
        y += 12;
        for (String function : functions) {
            graphics.text(this.font, function, x, y, COLOR_GRAY, false);
            y += 11;
        }
    }

    private void renderTruth(GuiGraphicsExtractor graphics) {
        renderFieldLabels(graphics);

        if (truthError != null) {
            graphics.text(this.font, truthError, MAIN_X, outputTop, COLOR_RED, false);
            return;
        }
        if (truthTable == null) {
            return;
        }

        List<String> variables = truthTable.variables();
        int headerTop = outputTop;
        int dataTop = headerTop + CELL_HEIGHT;
        int viewportBottom = this.height - 10;

        // Header row is pinned (doesn't scroll), so it's always readable above the data.
        int x = MAIN_X;
        for (String variable : variables) {
            drawCell(graphics, x, headerTop, CELL_WIDTH, variable, HEADER_CELL_COLOR);
            x += CELL_WIDTH;
        }
        drawCell(graphics, x, headerTop, CELL_WIDTH * 2, "result", HEADER_CELL_COLOR);

        for (int row = 0; row < truthTable.results().size(); row++) {
            int rowY = dataTop + row * CELL_HEIGHT - contentScrollOffset;
            if (rowY < dataTop || rowY > viewportBottom) {
                continue; // scrolled out of view, or would overlap the pinned header
            }
            boolean[] assignment = truthTable.assignments().get(row);
            int cellX = MAIN_X;
            for (boolean value : assignment) {
                drawCell(graphics, cellX, rowY, CELL_WIDTH, value ? "1" : "0",
                    value ? TRUE_CELL_COLOR : FALSE_CELL_COLOR);
                cellX += CELL_WIDTH;
            }
            boolean result = truthTable.results().get(row);
            drawCell(graphics, cellX, rowY, CELL_WIDTH * 2, result ? "1" : "0",
                result ? TRUE_CELL_COLOR : FALSE_CELL_COLOR);
        }
    }

    private void renderKmap(GuiGraphicsExtractor graphics) {
        renderFieldLabels(graphics);

        if (kmapError != null) {
            graphics.text(this.font, kmapError, MAIN_X, outputTop, COLOR_RED, false);
            return;
        }
        if (kmapTable == null) {
            return;
        }

        List<String> variables = kmapTable.variables();
        int n = variables.size();
        int rowBits = (n + 1) / 2;
        int colBits = n - rowBits;
        List<String> rowVars = variables.subList(0, rowBits);
        List<String> colVars = variables.subList(rowBits, n);

        // Corner cell names which variables are on which axis, e.g. "xy\z". That
        // label is often wider than a normal data cell (letters vs. a single
        // digit), so this column gets its own width instead of reusing CELL_WIDTH,
        // otherwise the text pokes out past the cell's left edge.
        String cornerLabel = String.join("", rowVars) + "\\" + String.join("", colVars);
        int rowHeaderWidth = Math.max(CELL_WIDTH, this.font.width(cornerLabel) + 6);

        int gridLeft = MAIN_X + rowHeaderWidth;
        int headerTop = outputTop;
        int dataTop = headerTop + CELL_HEIGHT;
        int viewportBottom = this.height - 10;

        drawCell(graphics, MAIN_X, headerTop, rowHeaderWidth, cornerLabel, HEADER_CELL_COLOR);

        renderKmapGrid(graphics, rowBits, colBits, rowHeaderWidth, gridLeft, headerTop, dataTop, viewportBottom);
    }

    private void renderKmapGrid(GuiGraphicsExtractor graphics, int rowBits, int colBits, int rowHeaderWidth,
                                 int gridLeft, int headerTop, int dataTop, int viewportBottom) {
        int cols = 1 << colBits;
        int rows = 1 << rowBits;

        for (int c = 0; c < cols; c++) {
            int grayC = c ^ (c >>> 1);
            String label = toBitString(grayC, colBits);
            drawCell(graphics, gridLeft + c * CELL_WIDTH, headerTop, CELL_WIDTH, label, HEADER_CELL_COLOR);
        }

        for (int r = 0; r < rows; r++) {
            int rowY = dataTop + r * CELL_HEIGHT - contentScrollOffset;
            if (rowY < dataTop || rowY > viewportBottom) {
                continue;
            }
            int grayR = r ^ (r >>> 1);
            String rowLabel = toBitString(grayR, rowBits);
            drawCell(graphics, MAIN_X, rowY, rowHeaderWidth, rowLabel, HEADER_CELL_COLOR);

            for (int c = 0; c < cols; c++) {
                int grayC = c ^ (c >>> 1);
                boolean[] assignment = new boolean[rowBits + colBits];
                for (int i = 0; i < rowBits; i++) {
                    assignment[i] = ((grayR >> (rowBits - 1 - i)) & 1) == 1;
                }
                for (int i = 0; i < colBits; i++) {
                    assignment[rowBits + i] = ((grayC >> (colBits - 1 - i)) & 1) == 1;
                }
                int mask = maskFromAssignment(assignment);
                boolean value = kmapTable.results().get(mask);
                drawCell(graphics, gridLeft + c * CELL_WIDTH, rowY, CELL_WIDTH, value ? "1" : "0",
                    value ? TRUE_CELL_COLOR : FALSE_CELL_COLOR);
            }
        }
    }

    /** The assignment array is in the same variable order the truth table used, so this recovers its row index. */
    private static int maskFromAssignment(boolean[] assignment) {
        int mask = 0;
        for (boolean bit : assignment) {
            mask = (mask << 1) | (bit ? 1 : 0);
        }
        return mask;
    }

    private static String toBitString(int value, int bitCount) {
        StringBuilder sb = new StringBuilder();
        for (int i = bitCount - 1; i >= 0; i--) {
            sb.append((value >> i) & 1);
        }
        return sb.toString();
    }

    /** Tab-separated text form of a truth table, for the Copy button (there's no ASCII form worth drawing, but pasting into a spreadsheet works fine). */
    private static String serializeTruthTable(BooleanAlgebra.TruthTable table) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.join("\t", table.variables())).append("\tresult\n");
        for (int row = 0; row < table.results().size(); row++) {
            for (boolean bit : table.assignments().get(row)) {
                sb.append(bit ? '1' : '0').append('\t');
            }
            sb.append(table.results().get(row) ? '1' : '0').append('\n');
        }
        return sb.toString();
    }

    /** Tab-separated text form of a k-map grid (Gray-code headers included), for the Copy button. */
    private static String serializeKmapTable(BooleanAlgebra.TruthTable table) {
        List<String> variables = table.variables();
        int n = variables.size();
        int rowBits = (n + 1) / 2;
        int colBits = n - rowBits;
        List<String> rowVars = variables.subList(0, rowBits);
        List<String> colVars = variables.subList(rowBits, n);
        int rows = 1 << rowBits;
        int cols = 1 << colBits;

        StringBuilder sb = new StringBuilder();
        sb.append(String.join("", rowVars)).append('\\').append(String.join("", colVars));
        for (int c = 0; c < cols; c++) {
            int grayC = c ^ (c >>> 1);
            sb.append('\t').append(toBitString(grayC, colBits));
        }
        sb.append('\n');

        for (int r = 0; r < rows; r++) {
            int grayR = r ^ (r >>> 1);
            sb.append(toBitString(grayR, rowBits));
            for (int c = 0; c < cols; c++) {
                int grayC = c ^ (c >>> 1);
                boolean[] assignment = new boolean[n];
                for (int i = 0; i < rowBits; i++) {
                    assignment[i] = ((grayR >> (rowBits - 1 - i)) & 1) == 1;
                }
                for (int i = 0; i < colBits; i++) {
                    assignment[rowBits + i] = ((grayC >> (colBits - 1 - i)) & 1) == 1;
                }
                int mask = maskFromAssignment(assignment);
                sb.append('\t').append(table.results().get(mask) ? '1' : '0');
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    private void drawCell(GuiGraphicsExtractor graphics, int x, int y, int width, String text, int backgroundColor) {
        graphics.fill(x, y, x + width, y + CELL_HEIGHT, backgroundColor);
        int textWidth = this.font.width(text);
        int textX = x + (width - textWidth) / 2;
        int textY = y + (CELL_HEIGHT - this.font.lineHeight) / 2;
        graphics.text(this.font, text, textX, textY, CELL_TEXT_COLOR, false);
    }

    private void renderHelp(GuiGraphicsExtractor graphics) {
        if (selectedHelpCommand == null) {
            graphics.text(this.font, "Pick a command on the left for details.", MAIN_X + 110, 24, COLOR_GRAY, false);
            return;
        }

        int detailX = MAIN_X + 110;
        int maxWidth = this.width - detailX - 10;
        int y = 20;
        graphics.text(this.font, capitalize(selectedHelpCommand), detailX, y, COLOR_WHITE, false);
        y += 12;
        graphics.text(this.font, ModCommands.usageFor(selectedHelpCommand), detailX, y, COLOR_GRAY, false);
        y += 12;
        for (String line : wrapText(ModCommands.shortDescriptionFor(selectedHelpCommand), maxWidth)) {
            graphics.text(this.font, line, detailX, y, COLOR_WHITE, false);
            y += 11;
        }
        y += 3;
        for (String line : wrapText(ModCommands.detailedHelpFor(selectedHelpCommand), maxWidth)) {
            graphics.text(this.font, line, detailX, y, COLOR_GRAY, false);
            y += 11;
        }
    }

    /** Greedily wraps text (respecting existing newlines as paragraph breaks) to fit within maxWidth pixels. */
    private List<String> wrapText(String text, int maxWidth) {
        List<String> lines = new ArrayList<>();
        for (String paragraph : text.split("\n", -1)) {
            StringBuilder currentLine = new StringBuilder();
            for (String word : paragraph.split(" ")) {
                String candidate = currentLine.isEmpty() ? word : currentLine + " " + word;
                if (this.font.width(candidate) > maxWidth && !currentLine.isEmpty()) {
                    lines.add(currentLine.toString());
                    currentLine = new StringBuilder(word);
                } else {
                    currentLine = new StringBuilder(candidate);
                }
            }
            lines.add(currentLine.toString());
        }
        return lines;
    }

    private static String capitalize(String s) {
        if (s.isEmpty()) {
            return s;
        }
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}