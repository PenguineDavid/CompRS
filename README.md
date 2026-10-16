# compRS

A client-side Fabric mod for Minecraft 26.2 that puts computational redstone tools inside the game: number-base and binary conversion, a math evaluator, boolean algebra simplification, truth tables and Karnaugh maps, redstone timing conversion, signal-strength barrels, and a few placement helpers.

Everything works two ways. You can type a chat command (for example `,conv 16 10 FF`), or you can use the in-game GUI. Both call exactly the same code, so they always give the same answer.

compRS does not design circuits for you. It is a set of tools to speed up the boring parts so you can spend more time designing.

## Contents

- [Requirements](#requirements)
- [Installation](#installation)
- [Quick start](#quick-start)
- [Chat commands](#chat-commands)
- [The GUI](#the-gui)
- [Placement helpers and settings](#placement-helpers-and-settings)
- [Using it on servers](#using-it-on-servers)
- [Building from source](#building-from-source)
- [Project layout](#project-layout)
- [Adding a command](#adding-a-command)
- [Tests](#tests)
- [Troubleshooting](#troubleshooting)
- [License and contact](#license-and-contact)

## Requirements

| Requirement | Version |
|---|---|
| Minecraft | 26.2 |
| Fabric Loader | 0.19.3 or newer |
| Fabric API | 0.152.1+26.2 (the version the mod is built against) |
| Java | 25 |

compRS is client-side only. It is not needed on the server, and it does not add blocks or items.

## Installation

1. Install Fabric Loader 0.19.3 or newer for Minecraft 26.2.
2. Put Fabric API into your `mods` folder.
3. Put `compRS-1.0.0.jar` into the same `mods` folder. Download it from the GitHub repository's releases page, or build it yourself (see [Building from source](#building-from-source)).
4. Launch the game.

## Quick start

- Open chat and type `,help` to list every command. The comma is what tells compRS the line is a command.
- Type `,compRS_GUI` to open the GUI, or click the **compRS** button in the top-left corner of the title screen or the pause menu.
- Type `,barrel 7` in Creative mode to get a barrel that makes a comparator read signal strength 7.

## Chat commands

Every command starts with a comma and is case-insensitive. Commands are handled entirely on your client and are never sent to the server. If a line starts with a comma but is not a compRS command, it is sent as ordinary chat.

Results appear in chat prefixed with `[CompRSHelper]`: green for results, red for errors. Hover a result and click it to copy just the value to your clipboard (for example `255`, not the whole sentence around it).

| Command | Purpose |
|---|---|
| [`,binary`](#binary) | Show a number as raw binary (integer, fixed-point, or IEEE 754 float) |
| [`,conv`](#conv) | Convert a number between bases 2 to 64 |
| [`,eval`](#eval) | Evaluate a math expression |
| [`,simplify`](#simplify) | Minimize a boolean expression |
| [`,truth`](#truth) | Truth tables (GUI only) |
| [`,delay`](#delay) | Convert between redstone ticks, game ticks, and milliseconds |
| [`,negate`](#negate) | Two's-complement negation of a bit string |
| [`,barrel`](#barrel) | Give yourself a barrel that outputs a chosen comparator signal |
| [`,help`](#help) | List commands or show details for one |
| [`,compRS_GUI`](#comprs_gui) | Open the GUI |

### binary

Converts a number to its raw binary representation.

| Form | Meaning |
|---|---|
| `,binary <number>` | Auto-detects integer or float. A decimal input is rounded to the nearest integer. Negative integers print as 32-bit two's complement. |
| `,binary fixed <intBits.fracBits> <number>` | Fixed-point Q format. The value is scaled by 2^fracBits, rounded, and zero-padded to `intBits + fracBits` bits. |
| `,binary float <number>` | IEEE 754 single precision (32 bits). |
| `,binary float single <number>` | Same as above, explicit. |
| `,binary float double <number>` | IEEE 754 double precision (64 bits). |

Examples:

```
,binary 10                 ->  10 in binary: 1010
,binary fixed 4.4 5        ->  5 as Q4.4: 01010000
,binary float 3.14         ->  3.14 as float32 (IEEE 754 single): 01000000010010001111010111000011
,binary float double 3.14  ->  3.14 as float64 (IEEE 754 double): 0100000000001001000111101011100001010001111010111000010100011111
```

### conv

`,conv <fromBase> <toBase> <number>` converts a number from one base to another. Bases 2 to 64 are supported.

- Digits are `0-9`, then `A-Z`, then `a-z`, then `+` and `/`. That gives every base up to 64 an unambiguous digit set (base 16 uses `0-9A-F`, base 36 uses `0-9A-Z`).
- For bases up to 36, letters are case-insensitive (`ff` and `FF` are the same in base 16). Above base 36, upper and lower case are different digit values.
- A leading `-` is accepted for negative numbers.
- Numbers can be arbitrarily large (they are not limited to what fits in a `long`).
- A digit that is not valid in the source base, or a base outside 2 to 64, is reported as an error instead of being silently misconverted.

Examples:

```
,conv 10 2 42        ->  42 (base 10) = 101010 (base 2)
,conv 16 10 FF       ->  FF (base 16) = 255 (base 10)
,conv 2 64 1010101   ->  1010101 (base 2) = 1L (base 64)
```

### eval

`,eval <expression>` evaluates a math expression.

- Operators: `+ - * /` and `mod` (either `x mod y` or `mod(x, y)`).
- Parentheses are supported. They are checked before evaluation, so unbalanced parentheses are reported as an error.
- Constants: `pi`, `e`, `tau`, `phi`.
- Functions: `sqrt`, `pow`, `log` (base 10), `abs`, `sin`, `cos`, `tan`. Trig functions work in degrees.
- Spaces are ignored, and names are case-insensitive.
- Whole-number results print without a decimal point.

Examples:

```
,eval 2 + 3 * 4              ->  14
,eval sqrt(16) + pow(2,5)    ->  36
,eval sin(90)                ->  1
,eval 10 mod 3               ->  1
,eval log(1000)              ->  3
```

### simplify

`,simplify <boolean expression>` finds a minimum sum-of-products form using the Quine-McCluskey method.

| Syntax | Meaning |
|---|---|
| `A`, `B`, `x1` | Variables: a letter, optionally followed by digits |
| `!A`, `~A`, `A'` | NOT (prefix `!` or `~`, or postfix `'`) |
| `&`, `*`, or `AB` | AND (juxtaposition counts, so `AB` means `A AND B`) |
| `^` | XOR |
| `\|`, `+` | OR |
| `( )` | Grouping |
| `0`, `1` | Literal false and true |

Precedence, highest to lowest: NOT, AND, XOR, OR. Up to 8 distinct variables are supported.

The exact minimum cover is selected. If that sum-of-products form is longer than what you typed (which can happen when XOR or factoring gives a shorter notation), your original expression is kept instead.

Examples:

```
,simplify A&B | A&!B        ->  Simplified expression: (A)
,simplify (A|B) & (A|!B)    ->  Simplified expression: (A)
,simplify A^B^A             ->  Simplified expression: (B)
```

### truth

Truth tables are a GUI feature. `,truth` in chat tells you so. Open the GUI and select **Truth** (see [The GUI](#the-gui)). The GUI also has a **Kmap** panel for Karnaugh maps.

### delay

`,delay <number> <fromUnit> <toUnit>` converts a delay between redstone ticks, game ticks, and milliseconds.

| Unit | Meaning |
|---|---|
| `rt` | Redstone tick, 1/10 second (2 game ticks) |
| `gt` | Game tick, 1/20 second |
| `ms` | Millisecond |

Examples:

```
,delay 5 rt gt     ->  10
,delay 200 ms rt   ->  2
,delay 1 gt ms     ->  50
```

### negate

`,negate <bits>` negates a two's-complement binary value by inverting every bit and adding 1, truncated to the bit string's own length. The length of what you type is the width (`0101` is treated as 4 bits), so no separate width argument is needed. It is the same operation in both directions, so negating a negative value's bit pattern gives back the positive one.

```
,negate 0101   ->  1011
,negate 1011   ->  0101
```

### barrel

`,barrel <signal strength>` gives you a barrel that makes a comparator read exactly that signal strength.

- Signal strength is a whole number from 1 to 15.
- Requires Creative mode.
- The barrel is filled with redstone, named `signal <n>`, and has an enchantment glint so it is easy to spot.
- It replaces whatever is in your selected hotbar slot.
- The same feature is available as the **Barrel** panel in the GUI.

Redstone needed in a barrel (27 slots) for each strength:

| Signal | 1 | 2 | 3 | 4 | 5 | 6 | 7 | 8 |
|---|---|---|---|---|---|---|---|---|
| Items | 1 | 124 | 247 | 371 | 494 | 618 | 741 | 864 |

| Signal | 9 | 10 | 11 | 12 | 13 | 14 | 15 |
|---|---|---|---|---|---|---|---|
| Items | 988 | 1111 | 1235 | 1358 | 1482 | 1605 | 1728 |

Each count is the minimum that reads that strength, and 15 is a completely full barrel.

### help

- `,help` lists every command with a one-line description.
- `,help <command>` shows full usage, supported syntax, and examples for that command (for example `,help conv`).

### compRS_GUI

`,compRS_GUI` opens the GUI. The screen opens on the next game tick, because opening it directly from inside chat handling would be undone when the chat screen closes.

## The GUI

Open it with `,compRS_GUI`, or with the **compRS** button in the top-left corner of the title screen and the pause menu.

The left sidebar switches between panels, and the main area on the right shows the selected panel. The sidebar scrolls with the mouse wheel when the pointer is over it, and the Truth and Kmap grids scroll when the pointer is over the main area.

### Panels

| Panel | What it does |
|---|---|
| Home | Overview of the mod, a short intro to computational redstone, and a click-to-copy address for the ORE server |
| Binary | Mode buttons (Integer, Fixed, Float). Fixed mode adds a Layout box, Float mode adds Single and Double buttons. |
| Eval | Expression box, plus a reference list of the available functions and constants |
| Simplify | Boolean expression box |
| Conv | From base, To base, and Number boxes |
| Truth | Builds a full truth table with a pinned header row. Green cells are 1 and red cells are 0. 2 to 6 variables. |
| Kmap | Builds a Karnaugh map with Gray-code row and column headers, so neighboring cells differ by exactly one variable. 2 to 6 variables. Rows use the first half of the variables, columns the rest. |
| Delay | Number box with button rows for the From and To units |
| Negate | Bits box |
| Help | A button per command. Click one for its usage, description, and examples; click it again to collapse it. |
| Barrel | Fifteen buttons, one per signal strength. The result of the last click is shown in green (success) or red (for example, not in Creative mode). |
| Settings | The placement toggles described below |
| Contact | Links to the developer's site and the project's GitHub |

### How the panels behave

- **Run and Copy.** Command panels have a Run button and a Copy button. Copy puts only the actual value on the clipboard. For Truth and Kmap it copies the grid as tab-separated text, so it pastes cleanly into a spreadsheet.
- **Selectable output.** Results appear in a real, read-only text box. You can highlight text in it and copy with Ctrl+C like any other text field.
- **Remembered input.** What you typed (and the button choices on Binary and Delay) is remembered when you switch panels, for the rest of the game session. It resets when you restart the game.
- **Same logic as chat.** Panels run the exact same code as the chat commands. Truth and Kmap are the exception, because a grid has no sensible chat form, so they use the boolean algebra code directly.

## Placement helpers and settings

Three optional helpers speed up common redstone building chores. All three are **off by default** and are toggled in the GUI under **Settings**.

| Setting | What it does |
|---|---|
| Auto subtract comparators | After you place a comparator, compRS right-clicks it once so it ends up in subtract mode. |
| Auto place slabs on top | Slabs are placed as top slabs, using the upper half of the block you are targeting. |
| Auto set repeater delay | Lets you choose a delay from 1 to 4 and applies it to every repeater you place (see below). |

### Choosing the repeater delay

With **Auto set repeater delay** on, hold a repeater, hold **Ctrl** (either side), and scroll the mouse wheel. Scrolling up raises the delay and scrolling down lowers it, between 1 and 4. The current value appears above the hotbar as `Repeater delay: <n>`. While you do this, the hotbar does not change slots.

Repeaters you place afterward are right-clicked until they reach the chosen delay. The scroll shortcut only works while no screen is open and a repeater is in your main hand.

### Where settings are stored

Settings are saved when you change them, in the game directory at:

```
config/comprs/settings.txt
```

It is a plain `key=value` properties file with these keys: `autoSubtractComparators`, `autoPlaceSlabsOnTop`, `autoSetRepeaterDelay`, and `repeaterDelay` (clamped to 1 to 4). A missing or unreadable file falls back to the defaults (all toggles off, delay 1).

## Using it on servers

compRS is client-side, so it can be used on servers that do not have it installed. The Home panel includes a tip for one common case: the ORE (Open Redstone Engineers) server runs Minecraft 1.21.8, while this mod targets 26.2. To join such a server while keeping the mod, use the official ViaFabricPlus mod, which lets a modern client join servers running other versions.

compRS is not affiliated with or sponsored by ORE.

## Building from source

You need a JDK 25. The project uses the Gradle wrapper, so nothing else needs installing.

```
gradlew build            # Windows
./gradlew build          # macOS, Linux, WSL
```

The mod jar is written to `build/libs/compRS-1.0.0.jar` (a sources jar is built next to it).

To launch a development client with the mod loaded:

```
gradlew runClient
```

Minecraft 26.2 ships unobfuscated, so the build has no mappings dependency on purpose. The class names in the game jar are already Mojang's official names, and Loom refuses a mappings entry in this situation.

## Project layout

```
src/main/java/io/github/penguinedavid/comprs/common/
  main.java                  Mod entry point, settings load and save, per-tick hook
  command/
    ModCommands.java         Command parsing, help text, and dispatch shared by chat and GUI
    BarrelGiver.java         Builds a signal barrel and gives it to the player
  gui/
    CompRSGuiScreen.java     The GUI screen and all of its panels
  mixin/                     Hooks into Minecraft (table below)
  util/
    BarrelSignal.java        Barrel signal parsing and redstone-count math
    BaseConverter.java       Base 2 to 64 conversion
    BooleanAlgebra.java      Expression parsing, truth tables, Quine-McCluskey
    ChatUtil.java            Colored, click-to-copy chat output
    GrayCode.java            Binary and Gray code conversion
    MathModule.java          The ,eval expression evaluator
    TimeConverter.java       rt, gt, and ms conversion
    TwosComplement.java      Two's-complement negation
src/main/resources/
  fabric.mod.json            Mod metadata
  comprs.mixins.json         Mixin configuration
src/test/java/...            JUnit 5 tests
```

### Mixins

| Mixin | Target | What it does |
|---|---|---|
| `ChatScreenMixin` | `ChatScreen#handleChatInput` | Intercepts chat lines that start with a comma and runs them as commands |
| `RepeaterScrollMixin` | `MouseHandler#onScroll` | Ctrl+scroll repeater delay selection |
| `RepeaterPlacementMixin` | `MultiPlayerGameMode#useItemOn` | Applies the chosen delay to newly placed repeaters |
| `ComparatorPlacementMixin` | `MultiPlayerGameMode#useItemOn` | Switches newly placed comparators to subtract mode |
| `SlabPlacementMixin` | `MultiPlayerGameMode#useItemOn` | Redirects slab placement to the upper half |
| `TitleScreenMixin` | `TitleScreen#init` | Adds the **compRS** button |
| `PauseScreenMixin` | `PauseScreen#init` | Adds the **compRS** button |
| `ScreenAccessor` | `Screen` | Invoker so the two screen mixins can add widgets |

These target Minecraft 26.2 internals. They are the most likely thing to break on a different Minecraft version.

## Adding a command

Command logic is written as pure methods that take the arguments and return a result, so it can be unit tested without a running game.

1. In `ModCommands`, add an entry to `COMMAND_HELP` (usage, short description, detailed help).
2. Add a `case` for it in `compute(...)` and in `isKnownCommand(...)`.
3. Write a `computeYourCommand(String[] rest)` method that returns `CommandResult.ok(...)` or `CommandResult.fail(...)`. Use the two-argument `ok(message, copyValue)` when the displayed text wraps the value (like `,conv`), so click-to-copy grabs only the value.
4. If the command needs the game (the player, inventory, and so on), keep that part out of the compute method, as `,barrel` does: pass in a function, such as `BarrelGiver::give`, so tests can supply a fake.
5. Add tests.

The GUI picks up the new entry automatically: it gets a sidebar button and appears in the Help panel. By default its panel is a single "Arguments" text box. To give it labeled fields, add an entry to `ARG_SPECS` in `CompRSGuiScreen`. For a fully custom panel, add a `case` in `init()` and in `extractRenderState(...)` (`barrel`, `binary`, and `delay` are examples).

## Tests

Run them with:

```
gradlew test
```

| Test class | Covers |
|---|---|
| `MathModuleTest` | The `,eval` evaluator, including modulo and the constants |
| `BooleanAlgebraTest` | Boolean expression parsing and simplification |
| `BarrelSignalTest` | Signal parsing and range checks, and that each redstone count makes a comparator read exactly the requested strength (and one fewer item would not) |
| `ModCommandsTest` | Command dispatch, barrel input validation with a fake giver, and the help entries |
| `MixinTargetsTest` | Every mixin source file is listed in `comprs.mixins.json`, and every listed mixin has a source file |

## Troubleshooting

- **The game crashes on startup with `Mixin apply ... failed`.** One of the mixins is targeting a class or field that does not exist in your Minecraft version. Check that you are on Minecraft 26.2 exactly. If you are developing the mod, the first `failed` line in the log names the broken mixin.
- **Typing a command just sends chat.** Commands must start with a comma and be a known command. Try `,help`.
- **`,barrel` says "Creative mode is required".** Switch to Creative mode.
- **A setting will not stick.** Settings are written to `config/comprs/settings.txt`. If that folder cannot be written, the setting still works for the current session but is lost on restart.

## License and contact

compRS is licensed under the GPLv3.

- Developer site: <https://penguinedavid.github.io>
- Source: <https://github.com/penguinedavid/compRS>
