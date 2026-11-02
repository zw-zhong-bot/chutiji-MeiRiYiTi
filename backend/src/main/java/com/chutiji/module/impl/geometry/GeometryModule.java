package com.chutiji.module.impl.geometry;

import java.math.BigDecimal;
import java.math.RoundingMode;
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
 * 几何图形模块：FIGURE 题型。
 *
 * <p>图形由服务端参数化生成并随 payload 下发（设计文档 10.3 裁定 3）。同一条生成路径先算答案、
 * 再按同一份参数画图，所以图形与答案天然对得上；答案仍只留在 answer 里，payload 只有 SVG 和作答提示。</p>
 */
@Component
public class GeometryModule implements QuestionModule {

    public static final String KEY = "geometry";

    static final String AXIS_SHAPE_COMPLEXITY = "shapeComplexity";
    static final String AXIS_NEED_AUXILIARY = "needAuxiliary";

    /** 判题容差（设计文档 6.6：1e-2）。 */
    static final double TOLERANCE = 1e-2;

    static final String BASIC = "BASIC";
    static final String COMPOSITE = "COMPOSITE";

    private static final String UNIT = "cm";

    private static final Pattern NUMBER = Pattern.compile("[-+]?\\d+(?:\\.\\d+)?");

    private static final ModuleMeta META = new ModuleMeta(
            KEY,
            "几何图形",
            "参数化几何题，SVG 图形随题下发",
            QuestionType.FIGURE,
            List.of(
                    DifficultyAxis.enumAxis(AXIS_SHAPE_COMPLEXITY, "图形复杂度", BASIC,
                            List.of(new AxisOption(BASIC, "基础"),
                                    new AxisOption(COMPOSITE, "复合"))),
                    DifficultyAxis.bool(AXIS_NEED_AUXILIARY, "是否需要辅助线", false)),
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
        boolean composite = COMPOSITE.equalsIgnoreCase(stringValue(difficulty.get(AXIS_SHAPE_COMPLEXITY), BASIC));
        boolean auxiliary = boolValue(difficulty.get(AXIS_NEED_AUXILIARY), false);
        ShapeKind[] kinds = ShapeKind.values();
        ShapeKind kind = kinds[randoms.get().nextInt(kinds.length)];

        GeometryProblem problem = new GeometryGenerator(randoms.get()).next(kind, composite, auxiliary);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("svg", problem.svg());
        payload.put("answerFormat", "NUMBER");
        payload.put("unit", UNIT);
        payload.put("hint", problem.hint());

        Map<String, Object> stored = new LinkedHashMap<>();
        stored.put("value", problem.value());
        stored.put("tolerance", TOLERANCE);
        stored.put("formula", problem.formula().name());
        stored.put("shapeKind", kind.name());
        for (Map.Entry<String, Double> entry : problem.given().entrySet()) {
            stored.put("given_" + entry.getKey(), entry.getValue());
        }

        return new GeneratedQuestion(problem.prompt(), payload, stored, problem.explanation());
    }

    @Override
    public GradeResult grade(GradeRequest request) {
        Map<String, Object> stored = request.storedAnswer();
        Object raw = stored == null ? null : stored.get("value");
        if (!(raw instanceof Number number)) {
            throw new BizException(2003, "题目答案缺失，无法判题");
        }
        double expected = number.doubleValue();
        double tolerance = TOLERANCE;
        Object rawTolerance = stored.get("tolerance");
        if (rawTolerance instanceof Number t) {
            tolerance = t.doubleValue();
        }
        String expectedText = format(expected);

        Double submitted = parseNumber(request.submitted());
        if (submitted == null) {
            return new GradeResult(false, expectedText, "答案格式不对，请输入一个数值");
        }
        boolean correct = Math.abs(submitted - expected) <= tolerance + 1e-9;
        return new GradeResult(correct, expectedText,
                correct ? "计算正确" : "计算不正确，标准答案 " + expectedText + " " + UNIT);
    }

    static String format(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP)
                .stripTrailingZeros().toPlainString();
    }

    /** 解析用户输入：容忍空格、中文单位后缀、千分位逗号；解析失败返回 null。 */
    static Double parseNumber(String submitted) {
        if (submitted == null) {
            return null;
        }
        String text = submitted.trim().replace("，", ",").replace("（", "(").replace("）", ")")
                .replace("cm", "").replace("厘米", "");
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

    private static String stringValue(Object raw, String fallback) {
        if (raw instanceof String text && !text.isBlank()) {
            return text.trim();
        }
        return fallback;
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
}
