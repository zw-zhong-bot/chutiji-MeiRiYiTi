package com.chutiji.module.impl.unit;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
 * 单位换算模块：FILL 题型。题面由内置单位表随机取同族两个单位生成，
 * 答案只留在 answer 里，判题按相对误差比较。
 */
@Component
public class UnitModule implements QuestionModule {

    public static final String KEY = "unit";

    static final String AXIS_FAMILY = "unitFamily";
    static final String AXIS_CROSS = "crossMagnitude";

    /** 判题相对误差（设计文档 6.5：默认 1e-3）。 */
    static final double RELATIVE_TOLERANCE = 1e-3;

    /** 跨 10^3 以上量级的阈值。 */
    private static final double CROSS_THRESHOLD = 1000.0;

    private static final int MAX_ATTEMPTS = 400;
    private static final double MIN_ANSWER = 0.001;
    private static final double MAX_ANSWER = 1_000_000.0;

    private static final Pattern NUMBER = Pattern.compile("[-+]?\\d+(?:\\.\\d+)?");

    private static final double[] NICE_VALUES = {
            0.25, 0.5, 0.75, 1, 1.5, 2, 2.5, 3, 3.5, 4, 4.5, 5, 6, 7, 8, 9, 10,
            12, 15, 20, 25, 30, 40, 50, 60, 80, 100, 120, 150, 200, 250, 300, 500, 1000};

    private static final ModuleMeta META = new ModuleMeta(
            KEY,
            "单位换算",
            "五个单位族内的数值换算，容差判题",
            QuestionType.FILL,
            List.of(
                    DifficultyAxis.enumAxis(AXIS_FAMILY, "单位族", UnitFamily.LENGTH.name(),
                            List.of(new AxisOption(UnitFamily.LENGTH.name(), "长度"),
                                    new AxisOption(UnitFamily.WEIGHT.name(), "质量"),
                                    new AxisOption(UnitFamily.VOLUME.name(), "容积"),
                                    new AxisOption(UnitFamily.TIME.name(), "时间"),
                                    new AxisOption(UnitFamily.AREA.name(), "面积"))),
                    DifficultyAxis.bool(AXIS_CROSS, "跨大量级", false)),
            false,
            120);

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
        UnitFamily family = UnitFamily.parse(difficulty.get(AXIS_FAMILY));
        boolean cross = boolValue(difficulty.get(AXIS_CROSS), false);
        Random random = randoms.get();

        List<UnitPair> pairs = candidatePairs(family, cross);
        if (pairs.isEmpty()) {
            throw new BizException(2007, "单位换算出题失败：" + family.name() + " 族没有符合量级要求的单位对");
        }
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            UnitPair pair = pairs.get(random.nextInt(pairs.size()));
            Double sourceValue = pickSourceValue(random, pair);
            if (sourceValue == null) {
                continue;
            }
            double answer = convert(sourceValue, pair.from(), pair.to());

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("answerFormat", "NUMBER");
            payload.put("unit", pair.to().name());
            payload.put("hint", "只填数值，单位是「" + pair.to().name() + "」");

            Map<String, Object> stored = new LinkedHashMap<>();
            stored.put("value", answer);

            String prompt = "单位换算：" + formatNumber(sourceValue) + " " + pair.from().name()
                    + " = ? " + pair.to().name();
            return new GeneratedQuestion(prompt, payload, stored,
                    formatNumber(sourceValue) + " " + pair.from().name() + " = "
                            + formatNumber(answer) + " " + pair.to().name());
        }
        throw new BizException(2007, "单位换算出题失败：未找到数值整洁的换算");
    }

    @Override
    public GradeResult grade(GradeRequest request) {
        Map<String, Object> stored = request.storedAnswer();
        Object raw = stored == null ? null : stored.get("value");
        if (!(raw instanceof Number number)) {
            throw new BizException(2003, "题目答案缺失，无法判题");
        }
        double expected = number.doubleValue();
        String expectedText = formatNumber(expected);

        Double submitted = parseNumber(request.submitted());
        if (submitted == null) {
            return new GradeResult(false, expectedText, "答案格式不对，请输入一个数值");
        }
        double tolerance = Math.max(Math.abs(expected) * RELATIVE_TOLERANCE, 1e-9);
        boolean correct = Math.abs(submitted - expected) <= tolerance;
        return new GradeResult(correct, expectedText,
                correct ? "换算正确" : "换算不正确，标准答案 " + expectedText);
    }

    private List<UnitPair> candidatePairs(UnitFamily family, boolean cross) {
        List<UnitPair> pairs = new ArrayList<>();
        List<UnitDef> units = family.units();
        for (UnitDef from : units) {
            for (UnitDef to : units) {
                if (from.equals(to)) {
                    continue;
                }
                double ratio = Math.max(from.factor(), to.factor()) / Math.min(from.factor(), to.factor());
                if (Math.abs(ratio - 1.0) < 1e-12) {
                    continue;
                }
                boolean crossPair = ratio >= CROSS_THRESHOLD;
                if (crossPair == cross) {
                    pairs.add(new UnitPair(from, to));
                }
            }
        }
        return pairs;
    }

    /**
     * 用 BigDecimal 换算，避开 double 的尾数垃圾（300 平方毫米 → 平方厘米 必须给 3，
     * 不能给 2.9999999999999996）。保留 12 位小数足以覆盖有意义的量级。
     */
    static double convert(double sourceValue, UnitDef from, UnitDef to) {
        BigDecimal result = BigDecimal.valueOf(sourceValue)
                .multiply(BigDecimal.valueOf(from.factor()))
                .divide(BigDecimal.valueOf(to.factor()), 12, java.math.RoundingMode.HALF_UP);
        return result.stripTrailingZeros().doubleValue();
    }

    /** 取一个"整洁"的源数值，使换算结果也整洁；取不到返回 null。 */
    private Double pickSourceValue(Random random, UnitPair pair) {
        for (int attempt = 0; attempt < 60; attempt++) {
            double value = NICE_VALUES[random.nextInt(NICE_VALUES.length)];
            double answer = convert(value, pair.from(), pair.to());
            double magnitude = Math.abs(answer);
            if (magnitude < MIN_ANSWER || magnitude > MAX_ANSWER) {
                continue;
            }
            if (!isNice(answer)) {
                continue;
            }
            return value;
        }
        return null;
    }

    /** 「整洁」= 十进制写法不超过 6 位小数，学生不会看到一个无限小数。 */
    static boolean isNice(double value) {
        if (value == 0) {
            return false;
        }
        return BigDecimal.valueOf(value).stripTrailingZeros().scale() <= 6;
    }

    static String formatNumber(double value) {
        return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }

    /** 解析用户输入：允许前后空格、中文单位后缀、千分位逗号；解析失败返回 null。 */
    static Double parseNumber(String submitted) {
        if (submitted == null) {
            return null;
        }
        String text = submitted.trim().replace("，", ",").replace("(", "").replace(")", "");
        text = text.replaceAll(",(?=\\d{3}(?!\\d))", "");
        Matcher matcher = NUMBER.matcher(text);
        if (!matcher.find()) {
            return null;
        }
        try {
            return Double.parseDouble(matcher.group());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static boolean boolValue(Object raw, boolean fallback) {
        if (raw instanceof Boolean value) {
            return value;
        }
        if (raw instanceof String text && !text.isBlank()) {
            return Boolean.parseBoolean(text.trim());
        }
        return fallback;
    }

    private record UnitPair(UnitDef from, UnitDef to) {
    }
}
