package io.github.penguinedavid.comprs.common.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MathModuleTest {
    @Test
    void evaluatesModuloInFunctionAndInfixForms() {
        assertEquals(1.0, MathModule.evaluate("mod(10, 3)"), 0.0);
        assertEquals(1.0, MathModule.evaluate("10 mod 3"), 0.0);
    }

    @Test
    void evaluatesModuloWithParenthesizedRightOperand() {
        assertEquals(2.0, MathModule.evaluate("10 mod (3 + 5)"), 0.0);
    }

    @Test
    void evaluatesMathematicalConstantsCaseInsensitively() {
        assertEquals(Math.E, MathModule.evaluate("E"), 1e-12);
        assertEquals(Math.E, MathModule.evaluate("e"), 1e-12);
        assertEquals(2 * Math.PI, MathModule.evaluate("TAU"), 1e-12);
        assertEquals((1 + Math.sqrt(5)) / 2, MathModule.evaluate("PHI"), 1e-12);
    }
}
