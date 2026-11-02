package com.chutiji.module.impl.arithmetic;

import java.math.BigDecimal;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 把用户输入解析为 Fraction。支持三种格式：
 * 1. 整数 / 分数：31、1/3、-5/6
 * 2. 小数：0.333（按 1e-6 容差与原值比较）
 * 3. 带分数：2 1/3、2-1/3、2又1/3
 */
public final class AnswerParser {

    private static final Pattern MIXED = Pattern.compile("^(-?\\d+)\\s*(?:\\s|又|-|_)\\s*(\\d+)/(\\d+)$");
    private static final Pattern FRACTION = Pattern.compile("^(-?\\d+)/(\\d+)$");
    private static final Pattern INTEGER = Pattern.compile("^-?\\d+$");
    private static final Pattern DECIMAL = Pattern.compile("^-?\\d*\\.\\d+$");

    /** 小数比较容差。 */
    public static final BigDecimal DECIMAL_TOLERANCE = new BigDecimal("0.000001");

    private AnswerParser() {
    }

    public record Parsed(Fraction fraction, boolean decimal) {
    }

    /**
     * 解析输入。无法解析时返回 null（判题按错处理）。
     */
    public static Parsed parse(String raw) {
        if (raw == null) {
            return null;
        }
        String s = raw.trim().replace(" ", "");
        if (s.isEmpty()) {
            return null;
        }
        // 带分数用原文判断（含空格或「又」）
        Parsed mixed = parseMixed(raw.trim());
        if (mixed != null) {
            return mixed;
        }
        if (INTEGER.matcher(s).matches()) {
            return new Parsed(Fraction.of(Long.parseLong(s)), false);
        }
        Matcher fm = FRACTION.matcher(s);
        if (fm.matches()) {
            long d = Long.parseLong(fm.group(2));
            if (d == 0) {
                return null;
            }
            return new Parsed(Fraction.of(Long.parseLong(fm.group(1)), d), false);
        }
        if (DECIMAL.matcher(s).matches()) {
            return new Parsed(decimalToFraction(new BigDecimal(s)), true);
        }
        return null;
    }

    private static Parsed parseMixed(String raw) {
        Matcher m = MIXED.matcher(raw.trim());
        if (!m.matches()) {
            return null;
        }
        long whole = Long.parseLong(m.group(1));
        long num = Long.parseLong(m.group(2));
        long den = Long.parseLong(m.group(3));
        if (den == 0) {
            return null;
        }
        Fraction frac = Fraction.of(num, den);
        Fraction total = whole < 0 ? Fraction.of(whole).subtract(frac) : Fraction.of(whole).add(frac);
        return new Parsed(total, false);
    }

    private static Fraction decimalToFraction(BigDecimal value) {
        BigDecimal stripped = value.stripTrailingZeros();
        int scale = stripped.scale();
        if (scale <= 0) {
            return Fraction.of(stripped.longValueExact());
        }
        long den = 1;
        for (int i = 0; i < scale; i++) {
            den *= 10;
        }
        return Fraction.of(stripped.unscaledValue().longValueExact(), den);
    }

    /** 用户答案是否等价于标准答案。 */
    public static boolean matches(String submitted, Fraction expected) {
        Parsed parsed = parse(submitted);
        if (parsed == null) {
            return false;
        }
        if (!parsed.decimal()) {
            return parsed.fraction().equals(expected);
        }
        // 小数按容差比较
        BigDecimal actual = new BigDecimal(parsed.fraction().numerator())
                .divide(new BigDecimal(parsed.fraction().denominator()), 12, java.math.RoundingMode.HALF_UP);
        BigDecimal target = new BigDecimal(expected.numerator())
                .divide(new BigDecimal(expected.denominator()), 12, java.math.RoundingMode.HALF_UP);
        return actual.subtract(target).abs().compareTo(DECIMAL_TOLERANCE) <= 0;
    }
}
