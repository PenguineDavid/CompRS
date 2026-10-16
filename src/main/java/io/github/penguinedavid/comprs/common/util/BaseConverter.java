package io.github.penguinedavid.comprs.common.util;

import java.math.BigInteger;

/**
 * Converts a number given in one base into another base, for bases 2 through 64.
 *
 * Digit alphabet (index = digit value): 0-9, then A-Z, then a-z, then '+' and '/',
 * for a total of 64 characters. This is the same convention used for RFC 4648
 * base64 digits shifted so that the familiar 0-9/A-Z ordering lines up with
 * bases people actually use day to day (hex, base36, etc.), while still giving
 * every base up to 64 an unambiguous, non-overlapping digit set.
 *
 * BigInteger is used throughout so arbitrarily large numbers (not just things
 * that fit in a long) convert correctly instead of silently overflowing.
 */
public final class BaseConverter {

    private BaseConverter() {
    }

    public static final int MIN_BASE = 2;
    public static final int MAX_BASE = 64;

    private static final String DIGITS =
        "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz+/";

    /** Thrown for an out-of-range base or a digit that isn't valid in the given base. */
    public static class BaseConversionException extends RuntimeException {
        public BaseConversionException(String message) {
            super(message);
        }
    }

    /**
     * Parses number as a value in fromBase, then renders it in toBase.
     * Accepts an optional leading '-' for negative numbers.
     */
    public static String convert(int fromBase, int toBase, String number) {
        validateBase(fromBase, "source");
        validateBase(toBase, "destination");

        if (number == null || number.isEmpty()) {
            throw new BaseConversionException("No number given to convert");
        }

        boolean negative = number.charAt(0) == '-';
        String digits = negative ? number.substring(1) : number;
        if (digits.isEmpty()) {
            throw new BaseConversionException("No digits given to convert");
        }

        BigInteger value = parse(digits, fromBase);
        if (negative) {
            value = value.negate();
        }

        return render(value, toBase);
    }

    private static void validateBase(int base, String label) {
        if (base < MIN_BASE || base > MAX_BASE) {
            throw new BaseConversionException(
                "Invalid " + label + " base " + base + ": must be between " + MIN_BASE + " and " + MAX_BASE);
        }
    }

    private static BigInteger parse(String digits, int base) {
        BigInteger bigBase = BigInteger.valueOf(base);
        BigInteger result = BigInteger.ZERO;
        for (int i = 0; i < digits.length(); i++) {
            char c = digits.charAt(i);
            // For base <= 36 there's no ambiguity between upper and lower case (only
            // 0-9 and A-Z are needed), so accept lowercase hex/base36 digits the way
            // people actually type them. Above base 36, upper and lower case are
            // distinct digit values (that's what gets us from 36 up to 64), so case
            // is preserved as-is there.
            char lookupChar = (base <= 36) ? Character.toUpperCase(c) : c;
            int value = DIGITS.indexOf(lookupChar);
            if (value < 0 || value >= base) {
                throw new BaseConversionException(
                    "'" + c + "' is not a valid digit in base " + base);
            }
            result = result.multiply(bigBase).add(BigInteger.valueOf(value));
        }
        return result;
    }

    private static String render(BigInteger value, int base) {
        if (value.signum() == 0) {
            return "0";
        }

        boolean negative = value.signum() < 0;
        BigInteger magnitude = value.abs();
        BigInteger bigBase = BigInteger.valueOf(base);

        StringBuilder sb = new StringBuilder();
        while (magnitude.signum() > 0) {
            BigInteger[] divRem = magnitude.divideAndRemainder(bigBase);
            sb.append(DIGITS.charAt(divRem[1].intValue()));
            magnitude = divRem[0];
        }
        if (negative) {
            sb.append('-');
        }
        return sb.reverse().toString();
    }
}