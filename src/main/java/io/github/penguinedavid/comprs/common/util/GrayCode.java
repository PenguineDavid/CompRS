package io.github.penguinedavid.comprs.common.util;

/**
 * Binary <-> Gray code conversion. A Gray code sequence changes only one bit
 * between consecutive values, which is exactly the property a Karnaugh map's
 * row/column headers need (so that adjacent cells differ by one variable).
 */
public final class GrayCode {

    private GrayCode() {
    }

    /** Converts a standard binary value to its Gray code equivalent. */
    public static int toGray(int binary) {
        return binary ^ (binary >>> 1);
    }

    /** Converts a Gray code value back to standard binary. */
    public static int fromGray(int gray) {
        int binary = 0;
        for (int mask = gray; mask != 0; mask >>>= 1) {
            binary ^= mask;
        }
        return binary;
    }
}