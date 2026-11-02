package com.chutiji.module.impl.algebra;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import com.chutiji.common.BizException;
import com.chutiji.module.impl.algebra.Polynomial.Term;

/**
 * 方程生成器：先定根，再反推系数，最后展开成标准式。
 * 生成阶段知道根；判题阶段不读这里的根，一律代回方程验证。
 */
final class AlgebraGenerator {

    /** 一道题：题面、判题用的方程（左 - 右）、落库答案、多元时的未知数顺序。 */
    record Puzzle(String prompt, String hint, List<Polynomial> equations,
            List<Rational> answers, List<String> unknowns) {
    }

    private record Equation(String text, Polynomial residual) {
    }

    private final Random random;

    AlgebraGenerator(Random random) {
        this.random = random;
    }

    Puzzle generate(Degree degree, int variables, boolean integerRoot) {
        return variables >= 2 ? system() : single(degree, integerRoot);
    }

    // ---------- 一元一次 / 一元二次 ----------

    private Puzzle single(Degree degree, boolean integerRoot) {
        if (degree == Degree.QUADRATIC) {
            long a = pickIn(1, 3);
            Rational first = pickRoot(integerRoot);
            Rational second = pickRoot(integerRoot);
            while (second.equals(first)) {
                second = pickRoot(integerRoot);
            }
            Rational sum = first.add(second);
            Rational product = first.multiply(second);
            // a(x - r1)(x - r2) = a·x² - a(r1+r2)x + a·r1r2
            Polynomial polynomial = Polynomial.of(
                    new Term(Rational.of(a), 2, 0),
                    new Term(Rational.of(a).multiply(sum).negate(), 1, 0),
                    new Term(Rational.of(a).multiply(product), 0, 0));
            if (polynomial.degree() != 2) {
                throw new BizException(2007, "二次方程生成失败：退化成一次");
            }
            if (integerRoot && !isPerfectSquare(discriminant(polynomial))) {
                throw new BizException(2007, "二次方程判别式不是完全平方");
            }
            List<Rational> answers = new ArrayList<>(List.of(first, second));
            answers.sort(null);
            return new Puzzle("解方程：" + polynomial.render() + " = 0",
                    "两个根用逗号或空格分开，如 3,-2；顺序不限",
                    List.of(polynomial), List.copyOf(answers), List.of("x"));
        }
        Rational root = pickRoot(integerRoot);
        Equation equation = random.nextBoolean() ? linearPlusConstant(root) : linearBothSides(root);
        if (!equation.residual().evaluate(root, Rational.ZERO).isZero()) {
            throw new BizException(2007, "一元一次方程生成失败：根不满足方程");
        }
        return new Puzzle(equation.text(), "直接写 x 的值，如 3 或 2/3",
                List.of(equation.residual()), List.of(root), List.of("x"));
    }

    /** a·x + b = c */
    private Equation linearPlusConstant(Rational root) {
        long a = pickNonZero(1, 9);
        long b = pickIn(-20, 20);
        Rational c = Rational.of(a).multiply(root).add(Rational.of(b));
        Polynomial left = Polynomial.of(
                new Term(Rational.of(a), 1, 0), new Term(Rational.of(b), 0, 0));
        Polynomial right = Polynomial.of(new Term(c, 0, 0));
        return new Equation("解方程：" + left.render() + " = " + right.render(), subtract(left, right));
    }

    /** a·x = b·x + c */
    private Equation linearBothSides(Rational root) {
        long a = pickSignedNonZero(1, 9);
        long b = pickSignedNonZero(1, 9);
        if (a == b) {
            b = -b;
        }
        Rational c = Rational.of(a - b).multiply(root);
        Polynomial left = Polynomial.of(new Term(Rational.of(a), 1, 0));
        Polynomial right = Polynomial.of(
                new Term(Rational.of(b), 1, 0), new Term(c, 0, 0));
        return new Equation("解方程：" + left.render() + " = " + right.render(), subtract(left, right));
    }

    // ---------- 二元一次方程组 ----------

    private Puzzle system() {
        long x = pickIn(-8, 8);
        long y = pickIn(-8, 8);
        while (y == x) {
            y = pickIn(-8, 8);
        }
        long a1;
        long b1;
        long a2;
        long b2;
        do {
            a1 = pickSignedNonZero(1, 6);
            b1 = pickSignedNonZero(1, 6);
            a2 = pickSignedNonZero(1, 6);
            b2 = pickSignedNonZero(1, 6);
        } while (a1 * b2 - a2 * b1 == 0);

        Equation first = systemRow(a1, b1, a1 * x + b1 * y);
        Equation second = systemRow(a2, b2, a2 * x + b2 * y);
        String prompt = "解方程组：" + first.text().substring("解方程：".length())
                + "；" + second.text().substring("解方程：".length());
        return new Puzzle(prompt, "按 x、y 的顺序写两个值，如 3,5",
                List.of(first.residual(), second.residual()),
                List.of(Rational.of(x), Rational.of(y)), List.of("x", "y"));
    }

    private Equation systemRow(long a, long b, long c) {
        Polynomial left = Polynomial.of(
                new Term(Rational.of(a), 1, 0), new Term(Rational.of(b), 0, 1));
        Polynomial right = Polynomial.of(new Term(Rational.of(c), 0, 0));
        return new Equation("解方程：" + left.render() + " = " + right.render(), subtract(left, right));
    }

    // ---------- 工具 ----------

    static Polynomial subtract(Polynomial left, Polynomial right) {
        List<Term> terms = new ArrayList<>(left.terms());
        for (Term term : right.terms()) {
            terms.add(new Term(term.coefficient().negate(), term.xPower(), term.yPower()));
        }
        return Polynomial.of(terms);
    }

    private static Rational discriminant(Polynomial quadratic) {
        Rational a = quadratic.coefficientOf(2);
        Rational b = quadratic.coefficientOf(1);
        Rational c = quadratic.coefficientOf(0);
        return b.multiply(b).subtract(Rational.of(4).multiply(a).multiply(c));
    }

    private static boolean isPerfectSquare(Rational value) {
        if (value.signum() < 0) {
            return false;
        }
        return isSquare(value.numerator()) && isSquare(value.denominator());
    }

    private static boolean isSquare(long value) {
        long root = Math.round(Math.sqrt((double) value));
        for (long candidate = Math.max(0, root - 2); candidate <= root + 2; candidate++) {
            if (candidate * candidate == value) {
                return true;
            }
        }
        return false;
    }

    /** 整数根直接取非零整数；否则取一个不能约分成整数的真分数根。 */
    private Rational pickRoot(boolean integerRoot) {
        if (integerRoot) {
            return Rational.of(pickNonZero(-9, 9));
        }
        long denominator = 2 + random.nextInt(3);
        long numerator;
        do {
            numerator = pickIn(-9, 9);
        } while (numerator == 0 || numerator % denominator == 0);
        return Rational.of(numerator, denominator);
    }

    private long pickIn(int min, int max) {
        return min + random.nextInt(max - min + 1);
    }

    private long pickNonZero(int min, int max) {
        long value = 0;
        while (value == 0) {
            value = pickIn(min, max);
        }
        return value;
    }

    private long pickSignedNonZero(int min, int max) {
        long value = pickNonZero(min, max);
        return random.nextBoolean() ? value : -value;
    }
}
