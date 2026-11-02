package com.chutiji.module.impl.arithmetic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

class ArithmeticGeneratorTest {

    private final ArithmeticGenerator generator = new ArithmeticGenerator(new Random(20261102L));

    @Test
    @DisplayName("生成 100 次，每次答案都能用分数独立校验")
    void generate100TimesAllAnswersCorrect() {
        List<ArithmeticGenerator.Config> configs = List.of(
                new ArithmeticGenerator.Config(100, 2, false, true),
                new ArithmeticGenerator.Config(100, 3, true, true),
                new ArithmeticGenerator.Config(1000, 4, true, true),
                new ArithmeticGenerator.Config(50, 1, false, false),
                new ArithmeticGenerator.Config(20, 3, true, false));

        for (int i = 0; i < 100; i++) {
            ArithmeticGenerator.Config config = configs.get(i % configs.size());
            Expr expr = generator.generate(config);
            assertNotNull(expr, "第 " + i + " 次生成不应为 null");

            Fraction expected = expr.evaluate();
            // 独立校验：把表达式树手工重算一遍，必须与 evaluate() 一致
            Fraction recomputed = recompute(expr);
            assertEquals(expected, recomputed, "第 " + i + " 次生成答案不一致: " + expr.render());

            // 不允许负数结果
            assertFalse(expected.isNegative(), "第 " + i + " 次出现负数: " + expr.render());

            // 禁止除法时必须整除
            if (!config.allowDivision()) {
                assertTrue(expected.isInteger(), "第 " + i + " 次禁止除法但结果非整数: " + expr.render());
            }
        }
    }

    @Test
    @DisplayName("除法除不尽时答案用分数表示，不用小数")
    void divisionRemainderUsesFraction() {
        Fraction value = Fraction.of(1, 3);
        assertEquals("1/3", value.toAnswerString());
        assertFalse(value.toAnswerString().contains("."));

        Fraction twoThirds = Fraction.of(2, 3);
        assertEquals("2/3", twoThirds.toAnswerString());

        // 构造一个除不尽的表达式：1 ÷ 3
        Expr expr = new Expr.Bin(Expr.Op.DIV, new Expr.Num(1), new Expr.Num(3));
        assertEquals("1/3", expr.evaluate().toAnswerString());
        assertEquals("1 ÷ 3", expr.render());
    }

    @Test
    @DisplayName("数值范围不超上限，运算符个数正确")
    void respectsConfig() {
        for (int i = 0; i < 50; i++) {
            Expr expr = generator.generate(new ArithmeticGenerator.Config(20, 2, false, true));
            assertTrue(maxLeaf(expr) <= 20, "叶子超出范围: " + expr.render());
            assertEquals(2, countOperators(expr), "运算符个数不对: " + expr.render());
        }
    }

    @RepeatedTest(20)
    @DisplayName("重复生成不抛异常、结果始终非负")
    void stableAcrossRuns() {
        Expr expr = generator.generate(new ArithmeticGenerator.Config(100, 4, true, true));
        assertNotNull(expr.evaluate());
        assertFalse(expr.evaluate().isNegative());
    }

    private Fraction recompute(Expr expr) {
        if (expr instanceof Expr.Num n) {
            return Fraction.of(n.value());
        }
        Expr.Bin bin = (Expr.Bin) expr;
        Fraction l = recompute(bin.left());
        Fraction r = recompute(bin.right());
        return switch (bin.op()) {
            case ADD -> l.add(r);
            case SUB -> l.subtract(r);
            case MUL -> l.multiply(r);
            case DIV -> l.divide(r);
        };
    }

    private long maxLeaf(Expr expr) {
        if (expr instanceof Expr.Num n) {
            return n.value();
        }
        Expr.Bin bin = (Expr.Bin) expr;
        return Math.max(maxLeaf(bin.left()), maxLeaf(bin.right()));
    }

    private int countOperators(Expr expr) {
        if (expr instanceof Expr.Num) {
            return 0;
        }
        Expr.Bin bin = (Expr.Bin) expr;
        return 1 + countOperators(bin.left()) + countOperators(bin.right());
    }
}
