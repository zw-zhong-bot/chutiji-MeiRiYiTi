package com.chutiji.module.impl.algebra;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.chutiji.common.BizException;

/**
 * 至多两个未知数（x、y）的多项式，一项 = 系数 × x^p × y^q。
 * 方程统一表示成「左边 - 右边」，于是「代回原方程」就是求值是否为 0。
 */
final class Polynomial {

    record Term(Rational coefficient, int xPower, int yPower) {
    }

    /** 次数高的在前，同次数 x 的在前，展示顺序稳定。 */
    private static final Comparator<Term> ORDER = Comparator
            .comparingInt((Term term) -> term.xPower() + term.yPower()).reversed()
            .thenComparing(Comparator.comparingInt(Term::xPower).reversed())
            .thenComparing(Comparator.comparingInt(Term::yPower).reversed());

    private final List<Term> terms;

    private Polynomial(List<Term> terms) {
        this.terms = terms;
    }

    static Polynomial of(List<Term> rawTerms) {
        Map<String, Term> merged = new LinkedHashMap<>();
        for (Term term : rawTerms) {
            if (term.coefficient().isZero()) {
                continue;
            }
            String key = term.xPower() + ":" + term.yPower();
            Term previous = merged.get(key);
            Rational coefficient = previous == null
                    ? term.coefficient()
                    : previous.coefficient().add(term.coefficient());
            merged.put(key, new Term(coefficient, term.xPower(), term.yPower()));
        }
        List<Term> cleaned = new ArrayList<>();
        for (Term term : merged.values()) {
            if (!term.coefficient().isZero()) {
                cleaned.add(term);
            }
        }
        cleaned.sort(ORDER);
        return new Polynomial(List.copyOf(cleaned));
    }

    static Polynomial of(Term... terms) {
        return of(List.of(terms));
    }

    List<Term> terms() {
        return terms;
    }

    /** 最高次数（单项里 x 次幂 + y 次幂 的最大值）。 */
    int degree() {
        int degree = 0;
        for (Term term : terms) {
            degree = Math.max(degree, term.xPower() + term.yPower());
        }
        return degree;
    }

    /** 指定次幂、单变量项的系数合计，如 x 的系数。 */
    Rational coefficientOf(int xPower) {
        Rational total = Rational.ZERO;
        for (Term term : terms) {
            if (term.xPower() == xPower && term.yPower() == 0) {
                total = total.add(term.coefficient());
            }
        }
        return total;
    }

    /** p(x, y) 的取值。 */
    Rational evaluate(Rational x, Rational y) {
        Rational total = Rational.ZERO;
        for (Term term : terms) {
            total = total.add(termValue(term, x, y));
        }
        return total;
    }

    /** 代入后各项绝对值之和，作为相对容差的分母。 */
    Rational magnitude(Rational x, Rational y) {
        Rational total = Rational.ZERO;
        for (Term term : terms) {
            Rational value = termValue(term, x, y);
            total = total.add(value.signum() < 0 ? value.negate() : value);
        }
        return total;
    }

    /** 标准式，如 "3x² - 5x + 2"。 */
    String render() {
        return renderTerms(terms);
    }

    /**
     * 落库编码：每项写成 "分母正化的分子/分母:y次幂:x次幂"，用 ':' 分隔，
     * 系数用分数写法因此不会和分隔符冲突。
     */
    List<String> encode() {
        List<String> encoded = new ArrayList<>(terms.size());
        for (Term term : terms) {
            encoded.add(term.coefficient().toText() + ":" + term.xPower() + ":" + term.yPower());
        }
        return List.copyOf(encoded);
    }

    static List<Polynomial> decode(List<?> rawEquations) {
        List<Polynomial> equations = new ArrayList<>();
        for (Object rawEquation : rawEquations) {
            if (!(rawEquation instanceof List<?> rawTerms)) {
                throw new BizException(2003, "题目答案格式非法：方程项列表缺失");
            }
            List<Term> terms = new ArrayList<>();
            for (Object rawTerm : rawTerms) {
                terms.add(decodeTerm(rawTerm));
            }
            equations.add(of(terms));
        }
        if (equations.isEmpty()) {
            throw new BizException(2003, "题目答案格式非法：没有方程");
        }
        return equations;
    }

    private static Term decodeTerm(Object raw) {
        if (!(raw instanceof String text)) {
            throw new BizException(2003, "题目答案格式非法：方程项不是文本");
        }
        String[] parts = text.split(":");
        if (parts.length != 3) {
            throw new BizException(2003, "题目答案格式非法: " + text);
        }
        Rational coefficient = Rational.parse(parts[0]);
        if (coefficient == null) {
            throw new BizException(2003, "方程系数非法: " + parts[0]);
        }
        try {
            return new Term(coefficient, Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
        } catch (NumberFormatException ex) {
            throw new BizException(2003, "方程项次数非法: " + text);
        }
    }

    private static Rational termValue(Term term, Rational x, Rational y) {
        Rational value = term.coefficient();
        for (int i = 0; i < term.xPower(); i++) {
            value = value.multiply(x);
        }
        for (int i = 0; i < term.yPower(); i++) {
            value = value.multiply(y);
        }
        return value;
    }

    private static String renderTerms(List<Term> terms) {
        StringBuilder builder = new StringBuilder();
        boolean first = true;
        for (Term term : terms) {
            if (term.coefficient().isZero()) {
                continue;
            }
            boolean negative = term.coefficient().signum() < 0;
            Rational magnitude = negative ? term.coefficient().negate() : term.coefficient();
            if (first) {
                if (negative) {
                    builder.append('-');
                }
                first = false;
            } else {
                builder.append(negative ? " - " : " + ");
            }
            String variablePart = variablePart(term);
            if (variablePart.isEmpty()) {
                builder.append(magnitude.toText());
            } else if (magnitude.equals(Rational.ONE)) {
                builder.append(variablePart);
            } else if (magnitude.isInteger()) {
                builder.append(magnitude.toText()).append(variablePart);
            } else {
                builder.append('(').append(magnitude.toText()).append(')').append(variablePart);
            }
        }
        return builder.length() == 0 ? "0" : builder.toString();
    }

    private static String variablePart(Term term) {
        StringBuilder builder = new StringBuilder();
        if (term.xPower() > 0) {
            builder.append('x');
            if (term.xPower() == 2) {
                builder.append('²');
            }
        }
        if (term.yPower() > 0) {
            if (builder.length() > 0) {
                builder.append('·');
            }
            builder.append('y');
            if (term.yPower() == 2) {
                builder.append('²');
            }
        }
        return builder.toString();
    }
}
