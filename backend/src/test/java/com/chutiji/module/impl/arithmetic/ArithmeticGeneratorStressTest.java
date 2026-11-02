package com.chutiji.module.impl.arithmetic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 用大量随机种子压生成器，确保任何参数组合都不会除零、出负数或除不尽被当整数。 */
class ArithmeticGeneratorStressTest {

    @Test
    @DisplayName("200 个随机种子 × 全参数组合：不出异常、结果非负、禁止除法时整除")
    void neverBreaksAcrossSeeds() {
        int generated = 0;
        for (int seed = 0; seed < 200; seed++) {
            ArithmeticGenerator generator = new ArithmeticGenerator(new Random(seed));
            for (int maxOperand : new int[] { 10, 20, 100, 1000 }) {
                for (int operatorCount = 1; operatorCount <= 4; operatorCount++) {
                    for (boolean paren : new boolean[] { false, true }) {
                        for (boolean div : new boolean[] { false, true }) {
                            ArithmeticGenerator.Config config =
                                    new ArithmeticGenerator.Config(maxOperand, operatorCount, paren, div);
                            Expr expr = generator.generate(config);
                            Fraction value = expr.evaluate();
                            assertFalse(value.isNegative(),
                                    "seed=" + seed + " 出现负数: " + expr.render());
                            if (!div) {
                                assertTrue(value.isInteger(),
                                        "seed=" + seed + " 禁止除法但非整数: " + expr.render());
                            }
                            generated++;
                        }
                    }
                }
            }
        }
        assertTrue(generated >= 6000, "实际生成次数 " + generated);
    }

    @Test
    @DisplayName("表达式渲染能还原成可独立求值的式子（括号正确）")
    void renderRoundTripsToSameValue() {
        ArithmeticGenerator generator = new ArithmeticGenerator(new Random(7));
        for (int i = 0; i < 200; i++) {
            Expr expr = generator.generate(new ArithmeticGenerator.Config(50, 3, true, true));
            String rendered = expr.render();
            assertFalse(rendered.contains("()"), "空括号: " + rendered);
            assertTrue(rendered.matches("[0-9+\\-×÷() ]+"), "非法字符: " + rendered);
            assertEquals(expr.evaluate(), evaluateRendered(rendered), "渲染求值不一致: " + rendered);
        }
    }

    /** 独立于 Expr 的小解析器，按标准优先级和左结合求值。 */
    private Fraction evaluateRendered(String text) {
        String compact = text.replace(" ", "").replace("×", "*").replace("÷", "/");
        return new Parser(compact).parseExpression();
    }

    private static final class Parser {
        private final String s;
        private int pos;

        Parser(String s) {
            this.s = s;
        }

        Fraction parseExpression() {
            Fraction value = parseTerm();
            while (pos < s.length() && (s.charAt(pos) == '+' || s.charAt(pos) == '-')) {
                char op = s.charAt(pos++);
                Fraction rhs = parseTerm();
                value = op == '+' ? value.add(rhs) : value.subtract(rhs);
            }
            return value;
        }

        Fraction parseTerm() {
            Fraction value = parseFactor();
            while (pos < s.length() && (s.charAt(pos) == '*' || s.charAt(pos) == '/')) {
                char op = s.charAt(pos++);
                Fraction rhs = parseFactor();
                value = op == '*' ? value.multiply(rhs) : value.divide(rhs);
            }
            return value;
        }

        Fraction parseFactor() {
            if (s.charAt(pos) == '(') {
                pos++;
                Fraction value = parseExpression();
                pos++;
                return value;
            }
            int start = pos;
            while (pos < s.length() && Character.isDigit(s.charAt(pos))) {
                pos++;
            }
            return Fraction.of(Long.parseLong(s.substring(start, pos)));
        }
    }
}
