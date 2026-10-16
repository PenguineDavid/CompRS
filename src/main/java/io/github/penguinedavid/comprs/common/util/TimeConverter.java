package io.github.penguinedavid.comprs.common.util;

/**
 * Converts between the timing units relevant to redstone: redstone ticks,
 * game ticks, and milliseconds.
 *
 * rt (redstone tick) = 1/10 second (a redstone tick is 2 game ticks)
 * gt (game tick)     = 1/20 second
 * ms (millisecond)   = 1/1000 second
 */
public final class TimeConverter {

    private TimeConverter() {
    }

    /** Thrown for an unrecognised unit. */
    public static class TimeUnitException extends RuntimeException {
        public TimeUnitException(String message) {
            super(message);
        }
    }

    private static final double SECONDS_PER_REDSTONE_TICK = 0.1;
    private static final double SECONDS_PER_GAME_TICK = 0.05;
    private static final double SECONDS_PER_MILLISECOND = 0.001;

    /** Converts value, given in fromUnit ("rt", "gt", or "ms"), into toUnit. */
    public static double convert(double value, String fromUnit, String toUnit) {
        double seconds = value * secondsPerUnit(fromUnit);
        return seconds / secondsPerUnit(toUnit);
    }

    private static double secondsPerUnit(String unit) {
        return switch (unit.toLowerCase()) {
            case "rt" -> SECONDS_PER_REDSTONE_TICK;
            case "gt" -> SECONDS_PER_GAME_TICK;
            case "ms" -> SECONDS_PER_MILLISECOND;
            default -> throw new TimeUnitException(
                "Unknown time unit '" + unit + "', expected one of: rt, gt, ms");
        };
    }
}