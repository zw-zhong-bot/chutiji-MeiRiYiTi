package com.chutiji.module.impl.algebra;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.chutiji.common.BizException;
import com.chutiji.module.api.AxisOption;
import com.chutiji.module.api.DifficultyAxis;
import com.chutiji.module.api.GenRequest;
import com.chutiji.module.api.GeneratedQuestion;
import com.chutiji.module.api.GradeRequest;
import com.chutiji.module.api.GradeResult;
import com.chutiji.module.api.ModuleMeta;
import com.chutiji.module.api.QuestionModule;
import com.chutiji.module.api.QuestionType;

/**
 * 方程与代数模块：FILL 题型。
 * 生成时先定根再反推系数；判题时把用户提交的整组值代回原方程验证，不做字符串比对。
 */
@Component
public class AlgebraModule implements QuestionModule {

    public static final String KEY = "algebra";

    static final String AXIS_DEGREE = "degree";
    static final String AXIS_VARIABLES = "variables";
    static final String AXIS_INTEGER_ROOT = "integerRoot";

    /** 代回验证的相对容差：分数答案被写成小数时仍然算对。 */
    static final BigDecimal RELATIVE_TOLERANCE = new BigDecimal("0.0000001");

    private static final ModuleMeta META = new ModuleMeta(
            KEY,
            "方程与代数",
            "一元一次、一元二次与二元一次方程，代回验证判题",
            QuestionType.FILL,
            List.of(
                    DifficultyAxis.enumAxis(AXIS_DEGREE, "方程次数", Degree.LINEAR.name(),
                            List.of(new AxisOption(Degree.LINEAR.name(), "一次"),
                                    new AxisOption(Degree.QUADRATIC.name(), "二次"))),
                    DifficultyAxis.intRange(AXIS_VARIABLES, "未知数个数", 1, 2, 1, 1),
                    DifficultyAxis.bool(AXIS_INTEGER_ROOT, "整数根", true)),
            false,
            180);

    private final ThreadLocal<Random> randoms = ThreadLocal.withInitial(Random::new);

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public ModuleMeta meta() {
        return META;
    }

    @Override
    public GeneratedQuestion generate(GenRequest request) {
        Map<String, Object> difficulty = request.difficulty();
        Degree degree = Degree.parse(difficulty.get(AXIS_DEGREE));
        int variables = Math.max(1, Math.min(2, intValue(difficulty.get(AXIS_VARIABLES), 1)));
        boolean integerRoot = boolValue(difficulty.get(AXIS_INTEGER_ROOT), true);

        AlgebraGenerator generator = new AlgebraGenerator(randoms.get());
        AlgebraGenerator.Puzzle puzzle = generator.generate(degree, variables, integerRoot);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("answerFormat", "TEXT");
        payload.put("hint", puzzle.hint());
        payload.put("unknowns", puzzle.unknowns());

        // 答案落库：方程编码用于「代回原方程」，标准答案只用于判完回显
        List<List<String>> equations = new ArrayList<>();
        for (Polynomial equation : puzzle.equations()) {
            equations.add(equation.encode());
        }
        Map<String, Object> stored = new LinkedHashMap<>();
        stored.put("equations", equations);
        stored.put("unknowns", puzzle.unknowns());
        List<String> solutions = puzzle.answers().stream().map(Rational::toText).toList();
        stored.put("solutions", solutions);

        return new GeneratedQuestion(puzzle.prompt(), payload, stored,
                describe(puzzle));
    }

    @Override
    public GradeResult grade(GradeRequest request) {
        Map<String, Object> stored = request.storedAnswer();
        if (stored == null || !(stored.get("equations") instanceof List<?> rawEquations)) {
            throw new BizException(2003, "题目答案缺失，无法判题");
        }
        List<Polynomial> equations = Polynomial.decode(rawEquations);
        List<String> unknowns = readUnknowns(stored);
        String correctAnswer = correctAnswerText(stored);

        List<SolutionParser.Entry> entries = SolutionParser.parse(request.submitted());
        if (entries == null) {
            return new GradeResult(false, correctAnswer, "答案格式不对，请写出未知数的值");
        }
        return unknowns.size() > 1
                ? gradeSystem(entries, equations, unknowns, correctAnswer)
                : gradeSingle(entries, equations, correctAnswer);
    }

    // ---------- 一元：答案是若干根，顺序无关 ----------

    private GradeResult gradeSingle(List<SolutionParser.Entry> entries, List<Polynomial> equations,
            String correctAnswer) {
        int expectedCount = expectedRootCount(equations.get(0));
        List<Rational> values = new ArrayList<>();
        // 只有每个值都写了 x= 才按标签解释，否则按位置解释（x=3或-2 属于后者）。
        // 一元题里 x1=3, x2=-2 的下标只是根的编号，不当作「同一个未知数写了两次」。
        boolean labeled = allLabeled(entries);
        for (SolutionParser.Entry entry : entries) {
            if (labeled && !"x".equalsIgnoreCase(entry.label())) {
                return new GradeResult(false, correctAnswer, "题目里没有未知数 " + entry.label());
            }
            values.add(entry.value());
        }
        if (values.size() != expectedCount) {
            return new GradeResult(false, correctAnswer,
                    "这道题有 " + expectedCount + " 个根，实际写了 " + values.size() + " 个");
        }
        Set<String> distinct = new LinkedHashSet<>();
        for (Rational value : values) {
            distinct.add(value.toText());
        }
        if (distinct.size() != values.size()) {
            return new GradeResult(false, correctAnswer, "根不能重复");
        }
        for (Rational value : values) {
            if (!satisfiesAll(List.of(value), equations, false)) {
                return new GradeResult(false, correctAnswer, value.toText() + " 不是原方程的根");
            }
        }
        return new GradeResult(true, correctAnswer, "代入原方程成立");
    }

    // ---------- 多元：按声明的未知数顺序，标签优先 ----------

    private GradeResult gradeSystem(List<SolutionParser.Entry> entries, List<Polynomial> equations,
            List<String> unknowns, String correctAnswer) {
        if (entries.size() != unknowns.size()) {
            return new GradeResult(false, correctAnswer,
                    "应当写 " + unknowns.size() + " 个值，实际写了 " + entries.size() + " 个");
        }
        List<Rational> values = new ArrayList<>();
        if (!allLabeled(entries)) {
            for (SolutionParser.Entry entry : entries) {
                values.add(entry.value());
            }
            return verify(values, equations, unknowns, correctAnswer);
        }
        Rational[] slots = new Rational[unknowns.size()];
        Set<String> seen = new LinkedHashSet<>();
        for (SolutionParser.Entry entry : entries) {
            String normalized = entry.label().toLowerCase();
            int index = unknowns.indexOf(normalized);
            if (index < 0) {
                return new GradeResult(false, correctAnswer, "题目里没有未知数 " + entry.label());
            }
            if (!seen.add(normalized)) {
                return new GradeResult(false, correctAnswer, "未知数 " + entry.label() + " 写了不止一次");
            }
            slots[index] = entry.value();
        }
        for (Rational slot : slots) {
            if (slot == null) {
                return new GradeResult(false, correctAnswer, "还有未知数没有作答");
            }
            values.add(slot);
        }
        return verify(values, equations, unknowns, correctAnswer);
    }

    private static int expectedRootCount(Polynomial equation) {
        return equation.degree() >= 2 ? 2 : 1;
    }

    /** 每个条目都自带 x= / y= 标签时才按标签解释。 */
    private static boolean allLabeled(List<SolutionParser.Entry> entries) {
        for (SolutionParser.Entry entry : entries) {
            if (entry.label() == null) {
                return false;
            }
        }
        return true;
    }

    private GradeResult verify(List<Rational> values, List<Polynomial> equations,
            List<String> unknowns, String correctAnswer) {
        if (!satisfiesAll(values, equations, true)) {
            StringBuilder detail = new StringBuilder();
            for (int i = 0; i < unknowns.size(); i++) {
                if (i > 0) {
                    detail.append("、");
                }
                detail.append(unknowns.get(i)).append("=").append(values.get(i).toText());
            }
            return new GradeResult(false, correctAnswer, detail + " 代回原方程不成立");
        }
        return new GradeResult(true, correctAnswer, "代入原方程成立");
    }

    /** 代回验证：每个方程都要成立；一元多解时逐个代入。 */
    private static boolean satisfiesAll(List<Rational> values, List<Polynomial> equations, boolean system) {
        if (system) {
            Rational x = values.get(0);
            Rational y = values.size() > 1 ? values.get(1) : Rational.ZERO;
            for (Polynomial equation : equations) {
                if (!isZeroWithinTolerance(equation, x, y)) {
                    return false;
                }
            }
            return true;
        }
        Rational root = values.get(0);
        for (Polynomial equation : equations) {
            if (!isZeroWithinTolerance(equation, root, Rational.ZERO)) {
                return false;
            }
        }
        return true;
    }

    private static boolean isZeroWithinTolerance(Polynomial equation, Rational x, Rational y) {
        Rational value = equation.evaluate(x, y);
        if (value.isZero()) {
            return true;
        }
        Rational magnitude = equation.magnitude(x, y);
        if (magnitude.isZero()) {
            return false;
        }
        BigDecimal residual = BigDecimal.valueOf(value.numerator())
                .divide(BigDecimal.valueOf(value.denominator()), 18, java.math.RoundingMode.HALF_UP);
        BigDecimal scale = BigDecimal.valueOf(magnitude.numerator())
                .divide(BigDecimal.valueOf(magnitude.denominator()), 18, java.math.RoundingMode.HALF_UP);
        return residual.abs().compareTo(scale.abs().multiply(RELATIVE_TOLERANCE)) <= 0;
    }

    private static List<String> readUnknowns(Map<String, Object> stored) {
        Object raw = stored.get("unknowns");
        if (!(raw instanceof List<?> list) || list.isEmpty()) {
            return List.of("x");
        }
        List<String> unknowns = new ArrayList<>(list.size());
        for (Object item : list) {
            unknowns.add(String.valueOf(item).toLowerCase());
        }
        return unknowns;
    }

    private static String correctAnswerText(Map<String, Object> stored) {
        Object raw = stored.get("solutions");
        if (!(raw instanceof List<?> list) || list.isEmpty()) {
            throw new BizException(2003, "题目答案格式非法：缺少 solutions");
        }
        List<String> solutions = list.stream().map(String::valueOf).toList();
        return String.join(",", solutions);
    }

    private String describe(AlgebraGenerator.Puzzle puzzle) {
        StringBuilder builder = new StringBuilder("标准答案 ");
        for (int i = 0; i < puzzle.unknowns().size(); i++) {
            if (i > 0) {
                builder.append("，");
            }
            builder.append(puzzle.unknowns().get(i)).append("=").append(puzzle.answers().get(i).toText());
        }
        return builder.toString();
    }

    private int intValue(Object raw, int fallback) {
        if (raw instanceof Number number) {
            return number.intValue();
        }
        if (raw instanceof String text && !text.isBlank()) {
            try {
                return Integer.parseInt(text.trim());
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    private boolean boolValue(Object raw, boolean fallback) {
        if (raw instanceof Boolean value) {
            return value;
        }
        if (raw instanceof String text && !text.isBlank()) {
            return Boolean.parseBoolean(text.trim());
        }
        return fallback;
    }
}
