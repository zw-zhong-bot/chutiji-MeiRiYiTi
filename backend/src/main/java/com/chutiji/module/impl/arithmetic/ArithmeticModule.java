package com.chutiji.module.impl.arithmetic;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.chutiji.common.BizException;
import com.chutiji.module.api.DifficultyAxis;
import com.chutiji.module.api.GenRequest;
import com.chutiji.module.api.GeneratedQuestion;
import com.chutiji.module.api.GradeRequest;
import com.chutiji.module.api.GradeResult;
import com.chutiji.module.api.ModuleMeta;
import com.chutiji.module.api.QuestionModule;
import com.chutiji.module.api.QuestionType;

/** 四则口算模块：FILL 题型，分数精确计算。 */
@Component
public class ArithmeticModule implements QuestionModule {

    public static final String KEY = "arithmetic";

    private static final ModuleMeta META = new ModuleMeta(
            KEY,
            "四则口算",
            "加减乘除混合运算",
            QuestionType.FILL,
            List.of(
                    DifficultyAxis.intRange("maxOperand", "数值范围", 10, 1000, 10, 100),
                    DifficultyAxis.intRange("operatorCount", "运算符个数", 1, 4, 1, 2),
                    DifficultyAxis.bool("allowParentheses", "允许括号", false),
                    DifficultyAxis.bool("allowDivision", "允许除法", true)),
            false,
            120);

    private final ArithmeticGenerator generator = new ArithmeticGenerator();

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
        ArithmeticGenerator.Config config = readConfig(request.difficulty());
        Expr expr = generator.generate(config);
        Fraction value = expr.evaluate();
        String answer = value.toAnswerString();

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("answerFormat", value.isInteger() ? "NUMBER" : "FRACTION");
        payload.put("hint", value.isInteger() ? "结果保留整数" : "结果用分数表示，如 3/4");

        Map<String, Object> stored = new LinkedHashMap<>();
        stored.put("value", answer);

        return new GeneratedQuestion("计算：" + expr.render() + " = ?", payload, stored,
                "标准答案 " + answer);
    }

    @Override
    public GradeResult grade(GradeRequest request) {
        Object raw = request.storedAnswer() == null ? null : request.storedAnswer().get("value");
        if (raw == null) {
            throw new BizException(2003, "题目答案缺失，无法判题");
        }
        String expectedText = raw.toString();
        Fraction expected = parseExpected(expectedText);
        boolean correct = AnswerParser.matches(request.submitted(), expected);
        return new GradeResult(correct, expectedText, "标准答案 " + expectedText);
    }

    private Fraction parseExpected(String text) {
        AnswerParser.Parsed parsed = AnswerParser.parse(text);
        if (parsed == null) {
            throw new BizException(2003, "题目答案格式非法: " + text);
        }
        return parsed.fraction();
    }

    private ArithmeticGenerator.Config readConfig(Map<String, Object> difficulty) {
        int maxOperand = intValue(difficulty.get("maxOperand"), 100);
        int operatorCount = intValue(difficulty.get("operatorCount"), 2);
        boolean allowParentheses = boolValue(difficulty.get("allowParentheses"), false);
        boolean allowDivision = boolValue(difficulty.get("allowDivision"), true);
        maxOperand = Math.max(10, Math.min(1000, maxOperand));
        operatorCount = Math.max(1, Math.min(4, operatorCount));
        return new ArithmeticGenerator.Config(maxOperand, operatorCount, allowParentheses, allowDivision);
    }

    private int intValue(Object raw, int fallback) {
        if (raw instanceof Number n) {
            return n.intValue();
        }
        if (raw instanceof String s && !s.isBlank()) {
            try {
                return Integer.parseInt(s.trim());
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    private boolean boolValue(Object raw, boolean fallback) {
        if (raw instanceof Boolean b) {
            return b;
        }
        if (raw instanceof String s && !s.isBlank()) {
            return Boolean.parseBoolean(s.trim());
        }
        return fallback;
    }
}
