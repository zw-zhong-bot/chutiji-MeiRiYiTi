package com.chutiji.module.impl.arithmetic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class FractionTest {

    @Test
    @DisplayName("分数构造自动约分、分母恒正")
    void normalizes() {
        assertEquals(1, Fraction.of(2, 4).numerator());
        assertEquals(2, Fraction.of(2, 4).denominator());
        assertEquals(-1, Fraction.of(1, -2).numerator());
        assertEquals(2, Fraction.of(1, -2).denominator());
    }

    @Test
    @DisplayName("四则运算精确，不经过 double")
    void arithmeticIsExact() {
        Fraction third = Fraction.of(1, 3);
        assertEquals(Fraction.of(2, 3), third.add(third));
        assertEquals(Fraction.of(1, 1), third.multiply(Fraction.of(3, 1)));
        assertEquals(Fraction.of(1, 9), third.multiply(third));
        assertEquals(Fraction.of(1, 2), third.divide(Fraction.of(2, 3)));
    }

    @Test
    @DisplayName("答案格式：整数不带分母，分数写 a/b")
    void answerFormat() {
        assertEquals("31", Fraction.of(62, 2).toAnswerString());
        assertEquals("1/3", Fraction.of(1, 3).toAnswerString());
        assertEquals("7/2", Fraction.of(7, 2).toAnswerString());
    }

    @Test
    @DisplayName("三分之一的三倍必须精确等于 1，double 会漂")
    void noFloatingPointDrift() {
        Fraction acc = Fraction.ZERO;
        for (int i = 0; i < 3; i++) {
            acc = acc.add(Fraction.of(1, 3));
        }
        assertTrue(acc.isInteger());
        assertEquals(Fraction.ONE, acc);
        assertFalse(0.1 + 0.2 == 0.3);
    }
}
