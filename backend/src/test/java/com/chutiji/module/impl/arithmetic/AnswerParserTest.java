package com.chutiji.module.impl.arithmetic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AnswerParserTest {

    @Test
    @DisplayName("整数、分数、小数、带分数四种写法都能解析")
    void parsesAllFormats() {
        assertEquals(Fraction.of(31), AnswerParser.parse("31").fraction());
        assertEquals(Fraction.of(1, 3), AnswerParser.parse("1/3").fraction());
        assertEquals(Fraction.of(-5, 6), AnswerParser.parse("-5/6").fraction());
        assertEquals(Fraction.of(7, 3), AnswerParser.parse("2 1/3").fraction());
        assertEquals(Fraction.of(7, 3), AnswerParser.parse("2-1/3").fraction());
        assertEquals(Fraction.of(7, 3), AnswerParser.parse("2又1/3").fraction());
        assertNotNull(AnswerParser.parse("0.5"));
    }

    @Test
    @DisplayName("非法输入返回 null，判题按错处理")
    void rejectsGarbage() {
        assertNull(AnswerParser.parse(""));
        assertNull(AnswerParser.parse("abc"));
        assertNull(AnswerParser.parse("1/0"));
        assertNull(AnswerParser.parse(null));
    }

    @Test
    @DisplayName("分数必须精确相等，小数按 1e-6 容差")
    void matching() {
        assertTrue(AnswerParser.matches("1/3", Fraction.of(1, 3)));
        assertTrue(AnswerParser.matches("2/6", Fraction.of(1, 3)));
        assertFalse(AnswerParser.matches("0.33", Fraction.of(1, 3)));
        assertTrue(AnswerParser.matches("0.333333", Fraction.of(1, 3)));
        assertTrue(AnswerParser.matches("2 1/3", Fraction.of(7, 3)));
        assertFalse(AnswerParser.matches("2.333", Fraction.of(7, 3)));
        assertFalse(AnswerParser.matches("abc", Fraction.of(1, 3)));
    }
}
