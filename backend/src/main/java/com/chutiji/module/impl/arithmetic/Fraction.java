package com.chutiji.module.impl.arithmetic;

import java.util.Objects;

/**
 * 精确分数：分子/分母均为 long，始终约分、分母恒正。
 * 四则口算的全部中间结果都走这里，禁止 double。
 */
public final class Fraction implements Comparable<Fraction> {

    public static final Fraction ZERO = new Fraction(0, 1);
    public static final Fraction ONE = new Fraction(1, 1);

    private final long numerator;
    private final long denominator;

    private Fraction(long numerator, long denominator) {
        this.numerator = numerator;
        this.denominator = denominator;
    }

    public static Fraction of(long numerator, long denominator) {
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
        return new Fraction(n / g, d / g);
    }

    public static Fraction of(long value) {
        return new Fraction(value, 1);
    }

    public Fraction add(Fraction other) {
        return of(Math.addExact(Math.multiplyExact(numerator, other.denominator),
                Math.multiplyExact(other.numerator, denominator)),
                Math.multiplyExact(denominator, other.denominator));
    }

    public Fraction subtract(Fraction other) {
        return of(Math.subtractExact(Math.multiplyExact(numerator, other.denominator),
                Math.multiplyExact(other.numerator, denominator)),
                Math.multiplyExact(denominator, other.denominator));
    }

    public Fraction multiply(Fraction other) {
        return of(Math.multiplyExact(numerator, other.numerator),
                Math.multiplyExact(denominator, other.denominator));
    }

    public Fraction divide(Fraction other) {
        if (other.isZero()) {
            throw new ArithmeticException("除数不能为 0");
        }
        return of(Math.multiplyExact(numerator, other.denominator),
                Math.multiplyExact(denominator, other.numerator));
    }

    public Fraction negate() {
        return new Fraction(-numerator, denominator);
    }

    public long numerator() {
        return numerator;
    }

    public long denominator() {
        return denominator;
    }

    public boolean isZero() {
        return numerator == 0;
    }

    public boolean isInteger() {
        return denominator == 1;
    }

    public boolean isNegative() {
        return numerator < 0;
    }

    /** 整数结果直接给整数，否则给 "分子/分母"。 */
    public String toAnswerString() {
        return isInteger() ? Long.toString(numerator) : numerator + "/" + denominator;
    }

    /** 展示用：整数原样，真分数原样，假分数保持 a/b（不做带分数转换）。 */
    @Override
    public String toString() {
        return toAnswerString();
    }

    @Override
    public int compareTo(Fraction other) {
        return Long.compare(Math.multiplyExact(numerator, other.denominator),
                Math.multiplyExact(other.numerator, denominator));
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof Fraction other)) {
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
