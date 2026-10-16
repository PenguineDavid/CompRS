package io.github.penguinedavid.comprs.common.util;

import java.math.BigInteger;

/**
 * Negates a binary string using two's complement (invert all bits, then add
 * 1, truncated to the string's own length). Since this is the same operation
 * in both directions, it also converts a negative two's-complement value back
 * to its positive magnitude.
 *
 * The width isn't a separate argument: the bit string's length already IS the
 * width (e.g. "0101" is unambiguously a 4-bit value), so asking for it
 * separately would just be redundant information the caller has to repeat.
 */
public final class TwosComplement {

    private TwosComplement() {
    }

    /** Thrown for a malformed or empty bit string. */
    public static class TwosComplementException extends RuntimeException {
        public TwosComplementException(String message) {
            super(message);
        }
    }

    /**
     * Negates bits (a string of '0'/'1' characters) via two's complement,
     * returning a binary string of the same width as the input.
     */
    public static String negate(String bits) {
        if (bits == null || bits.isEmpty()) {
            throw new TwosComplementException("No bits given to negate");
        }
        for (int i = 0; i < bits.length(); i++) {
            char c = bits.charAt(i);
            if (c != '0' && c != '1') {
                throw new TwosComplementException("'" + c + "' is not a valid binary digit (only 0 or 1)");
            }
        }

        int width = bits.length();
        BigInteger value = new BigInteger(bits, 2);
        BigInteger mask = BigInteger.ONE.shiftLeft(width).subtract(BigInteger.ONE);

        // Invert within the given width (equivalent to bitwise NOT truncated to
        // `width` bits), then add 1, then truncate back to `width` bits: this
        // truncation is what makes double-negation correctly round-trip even
        // for the one edge case where a width's most negative value has no
        // positive counterpart (e.g. 1000 in 4 bits negates back to itself).
        BigInteger inverted = mask.subtract(value);
        BigInteger negated = inverted.add(BigInteger.ONE).and(mask);

        String result = negated.toString(2);
        return zeroPad(result, width);
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
}