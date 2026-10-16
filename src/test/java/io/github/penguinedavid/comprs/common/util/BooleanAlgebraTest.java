package io.github.penguinedavid.comprs.common.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BooleanAlgebraTest {
    @Test
    void keepsCompactXorExpressionInsteadOfExpandingItToLongerSop() {
        String expression = "A&B^(X|D)";

        assertEquals(expression, BooleanAlgebra.simplify(expression));
    }

    @Test
    void reducesAnExpressionWhenItsMinimumSopIsShorter() {
        assertEquals("(A)", BooleanAlgebra.simplify("A&B | A&!B"));
    }
}
