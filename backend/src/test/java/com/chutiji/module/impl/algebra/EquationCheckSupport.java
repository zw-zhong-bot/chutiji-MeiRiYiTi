package com.chutiji.module.impl.algebra;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 测试侧独立验算：只吃题面文本，把「左边 = 右边」解析成可求值的项，
 * 不复用生产代码的 Polynomial/Rational。
 */
final class EquationCheckSupport {

    private static final Pattern TERM = Pattern.compile(
            "^(-)?(?:(\\((\\d+)/(\\d+)\\)|(\\d+(?:\\.\\d+)?)(?:/(\\d+))?))?([xy]?[²]?)$");

    private EquationCheckSupport() {
    }

    /** 一条方程：左边项与右边项，每项是 (系数, 变量次数字符串)。 */
    record Side(List<Term> terms) {
    }

    record Term(double coefficient, String variable) {
    }

    /** 解析一整条题面（"解方程：… = …" 或 "解方程组：… = …；… = …"），返回若干条方程。 */
    static List<List<Side>> parse(String prompt) {
        String body;
        if (prompt.startsWith("解方程组：")) {
            body = prompt.substring("解方程组：".length());
        } else if (prompt.startsWith("解方程：")) {
            body = prompt.substring("解方程：".length());
        } else {
            throw new AssertionError("题面前缀无法识别: " + prompt);
        }
        List<List<Side>> equations = new ArrayList<>();
        for (String equation : body.split("；")) {
            String[] sides = equation.split("=");
            if (sides.length != 2) {
                throw new AssertionError("方程无法切成左右两边: " + equation);
            }
            equations.add(List.of(parseSide(sides[0]), parseSide(sides[1])));
        }
        return equations;
    }

    private static Side parseSide(String text) {
        String normalized = text.trim().replace(" - ", " + -");
        List<Term> terms = new ArrayList<>();
        for (String part : normalized.split("\\+ ")) {
            if (part.isBlank()) {
                continue;
            }
            terms.add(parseTerm(part.trim()));
        }
        return new Side(terms);
    }

    private static Term parseTerm(String text) {
        Matcher matcher = TERM.matcher(text);
        if (!matcher.matches()) {
            throw new AssertionError("项无法解析: " + text);
        }
        boolean negative = matcher.group(1) != null;
        double coefficient;
        if (matcher.group(2) != null) {
            if (matcher.group(3) != null) {
                coefficient = Double.parseDouble(matcher.group(3)) / Double.parseDouble(matcher.group(4));
            } else {
                coefficient = Double.parseDouble(matcher.group(5));
                if (matcher.group(6) != null) {
                    coefficient /= Double.parseDouble(matcher.group(6));
                }
            }
        } else {
            coefficient = 1.0;
        }
        if (negative) {
            coefficient = -coefficient;
        }
        String variable = matcher.group(7) == null ? "" : matcher.group(7);
        return new Term(coefficient, variable);
    }

    private static double evaluate(Side side, double x, double y) {
        double total = 0;
        for (Term term : side.terms()) {
            double value = term.coefficient();
            value *= switch (term.variable()) {
                case "x" -> x;
                case "x²" -> x * x;
                case "y" -> y;
                case "y²" -> y * y;
                case "" -> 1.0;
                default -> throw new AssertionError("未知变量: " + term.variable());
            };
            total += value;
        }
        return total;
    }

    /** 把一组值代回题面的每条方程，全部成立才返回 true。 */
    static boolean holds(String prompt, double... values) {
        double x = values.length > 0 ? values[0] : 0;
        double y = values.length > 1 ? values[1] : 0;
        for (List<Side> equation : parse(prompt)) {
            double residual = evaluate(equation.get(0), x, y) - evaluate(equation.get(1), x, y);
            double scale = Math.abs(evaluate(equation.get(0), x, y)) + Math.abs(evaluate(equation.get(1), x, y));
            if (Math.abs(residual) > 1e-9 * Math.max(1.0, scale)) {
                return false;
            }
        }
        return true;
    }

    /** 多项式次数：看题面左边最高次幂。 */
    static int degree(String prompt) {
        int degree = 0;
        for (List<Side> equation : parse(prompt)) {
            for (Side side : equation) {
                for (Term term : side.terms()) {
                    degree = Math.max(degree, switch (term.variable()) {
                        case "x²", "y²" -> 2;
                        case "x", "y" -> 1;
                        default -> 0;
                    });
                }
            }
        }
        return degree;
    }

    /** 题面涉及的未知数个数。 */
    static int variableCount(String prompt) {
        boolean x = false;
        boolean y = false;
        for (List<Side> equation : parse(prompt)) {
            for (Side side : equation) {
                for (Term term : side.terms()) {
                    if (term.variable().startsWith("x")) {
                        x = true;
                    }
                    if (term.variable().startsWith("y")) {
                        y = true;
                    }
                }
            }
        }
        return (x ? 1 : 0) + (y ? 1 : 0);
    }
}
