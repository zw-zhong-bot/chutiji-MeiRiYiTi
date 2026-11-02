package com.chutiji.module.impl.logic;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

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

/**
 * 逻辑推理模块：CHOICE 题型。题目由「4 个人、4 个位置」的排列题构成，
 * 生成阶段穷举全部 4! = 24 种排列验证唯一解，答案只下发选项字母。
 */
@Component
public class LogicModule implements QuestionModule {

    public static final String KEY = "logic";

    static final String AXIS_CONDITION_COUNT = "conditionCount";
    static final String AXIS_STEP_DEPTH = "stepDepth";

    private static final int MIN_CONDITIONS = 2;
    private static final int MAX_CONDITIONS = 6;
    private static final int DEFAULT_CONDITIONS = 3;
    private static final int MIN_STEP_DEPTH = 1;
    private static final int MAX_STEP_DEPTH = 4;
    private static final int DEFAULT_STEP_DEPTH = 2;

    private static final ModuleMeta META = new ModuleMeta(
            KEY,
            "逻辑推理",
            "排列条件推理，穷举验证唯一解",
            QuestionType.CHOICE,
            List.of(
                    DifficultyAxis.intRange(AXIS_CONDITION_COUNT, "条件数量",
                            MIN_CONDITIONS, MAX_CONDITIONS, 1, DEFAULT_CONDITIONS),
                    DifficultyAxis.intRange(AXIS_STEP_DEPTH, "推理深度",
                            MIN_STEP_DEPTH, MAX_STEP_DEPTH, 1, DEFAULT_STEP_DEPTH)),
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
        int conditionCount = clamp(intValue(difficulty.get(AXIS_CONDITION_COUNT), DEFAULT_CONDITIONS),
                MIN_CONDITIONS, MAX_CONDITIONS);
        int stepDepth = clamp(intValue(difficulty.get(AXIS_STEP_DEPTH), DEFAULT_STEP_DEPTH),
                MIN_STEP_DEPTH, MAX_STEP_DEPTH);

        LogicGenerator generator = new LogicGenerator(randoms.get());
        LogicGenerator.Puzzle puzzle = generator.generate(conditionCount, stepDepth);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("options", puzzle.options());
        payload.put("answerFormat", "CHOICE");

        Map<String, Object> stored = new LinkedHashMap<>();
        stored.put("correctOption", puzzle.correctOption());

        return new GeneratedQuestion(puzzle.prompt(), payload, stored, puzzle.explanation());
    }

    @Override
    public GradeResult grade(GradeRequest request) {
        Map<String, Object> stored = request.storedAnswer();
        Object raw = stored == null ? null : stored.get("correctOption");
        if (!(raw instanceof String correctOption) || correctOption.isBlank()) {
            throw new BizException(2003, "题目答案缺失，无法判题");
        }
        String expected = correctOption.trim().toUpperCase();
        String submitted = normalize(request.submitted());
        if (submitted == null) {
            return new GradeResult(false, expected, "请选择一个选项（A/B/C/D）");
        }
        boolean correct = submitted.equals(expected);
        return new GradeResult(correct, expected,
                correct ? "选择正确" : "选择错误，正确答案是 " + expected);
    }

    /** 只取第一个选项字母，允许 "b"、"B."、"B. 李四"、"选B" 这些写法。 */
    static String normalize(String submitted) {
        if (submitted == null) {
            return null;
        }
        String text = submitted.trim().toUpperCase();
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch >= 'A' && ch <= 'D') {
                return String.valueOf(ch);
            }
        }
        return null;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
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
}
