package com.chutiji.module.impl.arithmetic;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 四则口算题目生成：递归构建表达式树，全程 Fraction 精确计算。
 * 约束：
 * - 每个中间结果非负（小学口算口径）
 * - allowDivision=false 时每个中间结果都是整数
 * - 除不尽时答案是分数，绝不转小数
 */
public class ArithmeticGenerator {

    /** 生成参数。 */
    public record Config(int maxOperand, int operatorCount, boolean allowParentheses, boolean allowDivision) {
    }

    private static final int MAX_ATTEMPTS = 300;

    /** 除不尽的结果出现的百分比（allowDivision=true 时），用整数避免浮点参与任何运算。 */
    private static final int FRACTION_DIVISION_PERCENT = 35;

    private final Random random;

    public ArithmeticGenerator() {
        this(new Random());
    }

    public ArithmeticGenerator(Random random) {
        this.random = random;
    }

    public Expr generate(Config config) {
        int operators = clamp(config.operatorCount(), 1, 4);
        int max = Math.max(2, config.maxOperand());
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            Expr expr = config.allowParentheses()
                    ? buildTree(operators, max)
                    : buildFlat(operators, max, config.allowDivision());
            if (expr == null || !isEvaluable(expr)) {
                continue;
            }
            if (!isAcceptable(expr, config.allowDivision())) {
                continue;
            }
            return expr;
        }
        // 兜底：保证永不返回 null，也不违反"非负"约束
        return new Expr.Bin(Expr.Op.ADD, new Expr.Num(1), new Expr.Num(1));
    }

    /** 平铺链：((a op b) op c) ...，无括号歧义。 */
    private Expr buildFlat(int operators, int max, boolean allowDivision) {
        Expr acc = new Expr.Num(randomBetween(1, max));
        for (int i = 0; i < operators; i++) {
            Fraction current = acc.evaluate();
            Expr.Op op = pickOp(allowDivision);
            Expr right = pickRight(op, current, max, allowDivision);
            if (right == null) {
                return null;
            }
            acc = new Expr.Bin(op, acc, right);
        }
        return acc;
    }

    /**
     * 带括号的随机树：把运算符个数分给左右子树。
     * 左右子树本身已保证可安全求值，这里再兜住"除数为 0"和"减法出负数"两种情况。
     */
    private Expr buildTree(int operators, int max) {
        if (operators <= 0) {
            return new Expr.Num(randomBetween(1, max));
        }
        int leftOps = random.nextInt(operators);
        int rightOps = operators - 1 - leftOps;
        Expr left = buildTree(leftOps, max);
        Expr right = buildTree(rightOps, max);
        Fraction l = left.evaluate();
        Fraction r = right.evaluate();
        Expr.Op op = pickOp(true);
        if (op == Expr.Op.DIV && r.isZero()) {
            op = Expr.Op.ADD;
        }
        if (op == Expr.Op.SUB && l.subtract(r).isNegative()) {
            op = Expr.Op.ADD;
        }
        return new Expr.Bin(op, left, right);
    }

    private Expr.Op pickOp(boolean allowDivision) {
        Expr.Op[] ops = allowDivision
                ? new Expr.Op[] { Expr.Op.ADD, Expr.Op.SUB, Expr.Op.MUL, Expr.Op.DIV }
                : new Expr.Op[] { Expr.Op.ADD, Expr.Op.SUB, Expr.Op.MUL };
        return ops[random.nextInt(ops.length)];
    }

    /**
     * 按运算符挑一个合适的右操作数，返回 null 表示当前状态无法继续。
     */
    private Expr pickRight(Expr.Op op, Fraction current, int max, boolean allowDivision) {
        switch (op) {
            case ADD -> {
                return new Expr.Num(randomBetween(1, max));
            }
            case SUB -> {
                long upper = current.isInteger()
                        ? current.numerator()
                        : Math.floorDiv(current.numerator(), current.denominator());
                upper = Math.min(max, upper);
                if (upper < 1) {
                    return null;
                }
                return new Expr.Num(randomBetween(1, (int) upper));
            }
            case MUL -> {
                // 控制结果规模，避免位数爆炸
                long cap = current.isInteger() ? Math.max(1, (long) max * max / Math.max(1, current.numerator()))
                        : 1L;
                int upper = (int) Math.min(max, Math.max(1, cap));
                return new Expr.Num(randomBetween(1, upper));
            }
            case DIV -> {
                if (!allowDivision || current.isZero()) {
                    return null;
                }
                // 一部分题故意除不尽，答案以分数下发
                if (random.nextInt(100) < FRACTION_DIVISION_PERCENT) {
                    return new Expr.Num(randomBetween(2, max));
                }
                if (current.isInteger()) {
                    List<Integer> divisors = new ArrayList<>();
                    long value = current.numerator();
                    for (int d = 1; (long) d * d <= value; d++) {
                        if (value % d == 0) {
                            divisors.add(d);
                            long other = value / d;
                            if (other != d && other <= max) {
                                divisors.add((int) other);
                            }
                        }
                    }
                    if (divisors.isEmpty()) {
                        return null;
                    }
                    return new Expr.Num(divisors.get(random.nextInt(divisors.size())));
                }
                // 分数：除以同分母可消成整数
                long den = current.denominator();
                return den <= max ? new Expr.Num((int) den) : null;
            }
            default -> {
                return null;
            }
        }
    }

    /** 所有中间结果非负；禁止除法时全部为整数。 */
    private boolean isAcceptable(Expr expr, boolean allowDivision) {
        if (!isEvaluable(expr)) {
            return false;
        }
        for (Fraction value : collectValues(expr)) {
            if (value.isNegative()) {
                return false;
            }
            if (!allowDivision && !value.isInteger()) {
                return false;
            }
        }
        return true;
    }

    private List<Fraction> collectValues(Expr expr) {
        List<Fraction> values = new ArrayList<>();
        collect(expr, values);
        return values;
    }

    private void collect(Expr expr, List<Fraction> out) {
        if (expr instanceof Expr.Num) {
            out.add(expr.evaluate());
            return;
        }
        Expr.Bin bin = (Expr.Bin) expr;
        collect(bin.left(), out);
        collect(bin.right(), out);
        out.add(expr.evaluate());
    }

    /** 整棵树能否安全求值（除数为 0 会让 evaluate 抛异常）。 */
    private boolean isEvaluable(Expr expr) {
        try {
            expr.evaluate();
            return true;
        } catch (ArithmeticException ex) {
            return false;
        }
    }

    private int randomBetween(int min, int max) {
        if (max <= min) {
            return min;
        }
        return min + random.nextInt(max - min + 1);
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
