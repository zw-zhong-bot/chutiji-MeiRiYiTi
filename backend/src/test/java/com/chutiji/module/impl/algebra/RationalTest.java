package com.chutiji.module.impl.algebra;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RationalTest {

    @Test
    @DisplayName("解析：整数、负数、分数、小数，非法输入返回 null")
    void parsesAllForms() {
        assertEquals("3", Rational.parse("3").toText());
        assertEquals("-2", Rational.parse("-2").toText());
        assertEquals("2/3", Rational.parse("2/3").toText());
        assertEquals("2/3", Rational.parse("4/6").toText());
        assertEquals("1/4", Rational.parse("0.25").toText());
        assertEquals("-1/2", Rational.parse("-0.5").toText());
        assertEquals("5", Rational.parse("5.0").toText());
        assertNull(Rational.parse(null));
        assertNull(Rational.parse(""));
        assertNull(Rational.parse("abc"));
        assertNull(Rational.parse("1/0"));
    }

    @Test
    @DisplayName("运算是精确的：1/3 + 1/6 = 1/2，且恒约分、分母恒正")
    void arithmeticIsExact() {
        Rational third = Rational.of(1, 3);
        Rational sixth = Rational.of(1, 6);
        assertEquals("1/2", third.add(sixth).toText());
        assertEquals("-1/6", third.subtract(sixth).negate().toText());
        assertEquals("1/18", third.multiply(sixth).toText());
        assertEquals("2", Rational.of(-4, -2).toText());
        assertTrue(Rational.of(-4, -2).isInteger());
    }

    @Test
    @DisplayName("多项式求值：3x² - 5x + 2 在 x=1 时为 0，在 x=2 时为 4")
    void polynomialEvaluates() {
        Polynomial polynomial = Polynomial.of(
                new Polynomial.Term(Rational.of(3), 2, 0),
                new Polynomial.Term(Rational.of(-5), 1, 0),
                new Polynomial.Term(Rational.of(2), 0, 0));
        assertEquals("3x² - 5x + 2", polynomial.render());
        assertEquals("0", polynomial.evaluate(Rational.of(1), Rational.ZERO).toText());
        assertEquals("4", polynomial.evaluate(Rational.of(2), Rational.ZERO).toText());
        assertEquals(2, polynomial.degree());
    }

    @Test
    @DisplayName("编码往返：encode 后 decode 回同一多项式")
    void encodeRoundTrip() {
        Polynomial polynomial = Polynomial.of(
                new Polynomial.Term(Rational.of(1, 2), 2, 0),
                new Polynomial.Term(Rational.of(-3, 4), 0, 1),
                new Polynomial.Term(Rational.of(5), 0, 0));
        Polynomial decoded = Polynomial.decode(java.util.List.of(polynomial.encode())).get(0);
        assertEquals(polynomial.render(), decoded.render());
        assertEquals(polynomial.evaluate(Rational.of(2), Rational.of(3)).toText(),
                decoded.evaluate(Rational.of(2), Rational.of(3)).toText());
    }

    @Test
    @DisplayName("答案解析：支持 x=3、3、x=3或-2、3,-2、3 -2、x1=3,x2=-2")
    void solutionParserHandlesFormats() {
        assertEquals(1, SolutionParser.parse("3").size());
        assertEquals("x", SolutionParser.parse("x=3").get(0).label());
        assertEquals("2/3", SolutionParser.parse("x=2/3").get(0).value().toText());
        assertEquals(2, SolutionParser.parse("3,-2").size());
        assertEquals(2, SolutionParser.parse("3 -2").size());
        assertEquals(2, SolutionParser.parse("x=3或-2").size());
        assertEquals(2, SolutionParser.parse("x1=3, x2=-2").size());
        assertEquals("-2", SolutionParser.parse("3或-2").get(1).value().toText());
        assertNull(SolutionParser.parse("不知道"));
        assertNull(SolutionParser.parse(""));
    }
}
