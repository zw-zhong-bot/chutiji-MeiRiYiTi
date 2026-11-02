package com.chutiji.module.impl.algebra;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * 精确有理数：分子/分母均为 long，恒约分、分母恒正。
 * 系数推导与「代回原方程」验证全程走它，不使用浮点。
 */
final class Rational implements Comparable<Rational> {

    static final Rational ZERO = new Rational(0, 1);
    static final Rational ONE = new Rational(1, 1);

    private final long numerator;
    private final long denominator;

    private Rational(long numerator, long denominator) {
        this.numerator = numerator;
        this.denominator = denominator;
    }

    static Rational of(long value) {
        return new Rational(value, 1);
    }

    static Rational of(long numerator, long denominator) {
        if (denominator == 0) {
            throw new ArithmeticException("分母不能为 0");
        }
        long n = numerator;
        long d = denominator;
        if (d < 0) {
            n = -n;
            d = -d;
        }
        long g = gcd(Math.abs(n), d);
        if (g == 0) {
            g = 1;
        }
        return new Rational(n / g, d / g);
    }

    /** 解析 "3"、"-2"、"2/3"、"-0.25"；无法解析返回 null。 */
    static Rational parse(String raw) {
        if (raw == null) {
            return null;
        }
        String text = raw.trim();
        if (text.isEmpty()) {
            return null;
        }
        try {
            int slash = text.indexOf('/');
            if (slash > 0) {
                long numerator = Long.parseLong(text.substring(0, slash).trim());
                long denominator = Long.parseLong(text.substring(slash + 1).trim());
                return denominator == 0 ? null : of(numerator, denominator);
            }
            if (text.contains(".")) {
                BigDecimal value = new BigDecimal(text).stripTrailingZeros();
                int scale = value.scale();
                if (scale <= 0) {
                    return of(value.longValueExact());
                }
                long denominator = 1;
                for (int i = 0; i < scale; i++) {
                    denominator *= 10;
                }
                return of(value.unscaledValue().longValueExact(), denominator);
            }
            return of(Long.parseLong(text));
        } catch (NumberFormatException | ArithmeticException ex) {
            return null;
        }
    }

    Rational add(Rational other) {
        return of(Math.addExact(Math.multiplyExact(numerator, other.denominator),
                Math.multiplyExact(other.numerator, denominator)),
                Math.multiplyExact(denominator, other.denominator));
    }

    Rational subtract(Rational other) {
        return add(other.negate());
    }

    Rational multiply(Rational other) {
        return of(Math.multiplyExact(numerator, other.numerator),
                Math.multiplyExact(denominator, other.denominator));
    }

    Rational negate() {
        return new Rational(-numerator, denominator);
    }

    boolean isZero() {
        return numerator == 0;
    }

    boolean isInteger() {
        return denominator == 1;
    }

    int signum() {
        return Long.signum(numerator);
    }

    long numerator() {
        return numerator;
    }

    long denominator() {
        return denominator;
    }

    double toDouble() {
        return (double) numerator / (double) denominator;
    }

    /** 展示与落库都用这个写法：整数原样，分数写作 分子/分母。 */
    String toText() {
        return isInteger() ? Long.toString(numerator) : numerator + "/" + denominator;
    }

    @Override
    public String toString() {
        return toText();
    }

    @Override
    public int compareTo(Rational other) {
        return Long.compare(Math.multiplyExact(numerator, other.denominator),
                Math.multiplyExact(other.numerator, denominator));
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof Rational other)) {
            return false;
        }
        return numerator == other.numerator && denominator == other.denominator;
    }

    @Override
    public int hashCode() {
        return Objects.hash(numerator, denominator);
    }

    private static long gcd(long a, long b) {
        while (b != 0) {
            long t = a % b;
            a = b;
            b = t;
        }
        return Math.abs(a);
    }
}
