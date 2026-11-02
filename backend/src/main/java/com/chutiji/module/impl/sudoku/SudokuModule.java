package com.chutiji.module.impl.sudoku;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

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
 * 数独模块：BOARD 题型。题目只下发 grid，完整解留在 answer 里由服务端判题。
 * 难度轴由本模块自己声明，前端按 kind 渲染，不认识这两个轴。
 */
@Component
public class SudokuModule implements QuestionModule {

    public static final String KEY = "sudoku";

    static final String AXIS_TECHNIQUE = "techniqueLevel";
    static final String AXIS_SYMMETRY = "symmetry";

    private static final int SIZE = 9;
    private static final int CELLS = SIZE * SIZE;
    private static final int MAX_ATTEMPTS = 20;

    private static final ModuleMeta META = new ModuleMeta(
            KEY,
            "数独",
            "9×9 标准数独，按技巧等级控制难度",
            QuestionType.BOARD,
            List.of(
                    DifficultyAxis.enumAxis(AXIS_TECHNIQUE, "技巧等级", TechniqueLevel.EASY.code(),
                            List.of(new AxisOption(TechniqueLevel.EASY.code(), "入门"),
                                    new AxisOption(TechniqueLevel.MEDIUM.code(), "进阶"),
                                    new AxisOption(TechniqueLevel.HARD.code(), "困难"),
                                    new AxisOption(TechniqueLevel.EXPERT.code(), "专家"))),
                    DifficultyAxis.bool(AXIS_SYMMETRY, "对称挖洞", true)),
            false,
            600);

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
        TechniqueLevel targetLevel = TechniqueLevel.parse(difficulty.get(AXIS_TECHNIQUE));
        boolean symmetric = boolValue(difficulty.get(AXIS_SYMMETRY), true);

        SudokuGenerator.Puzzle puzzle = SudokuGenerator.generate(randoms.get(), targetLevel, symmetric, MAX_ATTEMPTS);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("size", SIZE);
        payload.put("grid", toGrid(puzzle.givens()));
        payload.put("answerFormat", "GRID");

        Map<String, Object> stored = new LinkedHashMap<>();
        stored.put("solution", toGrid(puzzle.solution()));

        String prompt = symmetric ? "数独（对称挖洞）" : "数独";
        return new GeneratedQuestion(prompt, payload, stored, describe(puzzle));
    }

    @Override
    public GradeResult grade(GradeRequest request) {
        List<Integer> solution = readSolution(request.storedAnswer());
        String correctAnswer = flatten(solution);

        int[] submitted = parseSubmission(request.submitted());
        if (submitted == null) {
            return new GradeResult(false, correctAnswer, "答案格式不对，应为 9 行共 81 个数字");
        }
        for (int cell = 0; cell < CELLS; cell++) {
            if (submitted[cell] != solution.get(cell)) {
                int row = cell / SIZE + 1;
                int column = cell % SIZE + 1;
                return new GradeResult(false, correctAnswer,
                        "第 " + row + " 行第 " + column + " 列填错了");
            }
        }
        return new GradeResult(true, correctAnswer, "全部填对");
    }

    private static String describe(SudokuGenerator.Puzzle puzzle) {
        int givens = 0;
        for (int value : puzzle.givens()) {
            if (value != 0) {
                givens++;
            }
        }
        return "技巧等级 " + puzzle.level().code() + "，已知数 " + givens + " 个，唯一解";
    }

    private static List<List<Integer>> toGrid(int[] cells) {
        List<List<Integer>> grid = new ArrayList<>(SIZE);
        for (int row = 0; row < SIZE; row++) {
            List<Integer> line = new ArrayList<>(SIZE);
            for (int column = 0; column < SIZE; column++) {
                line.add(cells[row * SIZE + column]);
            }
            grid.add(line);
        }
        return grid;
    }

    private static List<Integer> readSolution(Map<String, Object> storedAnswer) {
        Object raw = storedAnswer == null ? null : storedAnswer.get("solution");
        if (!(raw instanceof List<?> rows) || rows.size() != SIZE) {
            throw new BizException(2003, "题目答案缺失，无法判题");
        }
        List<Integer> flat = new ArrayList<>(CELLS);
        for (Object rowRaw : rows) {
            if (!(rowRaw instanceof List<?> row) || row.size() != SIZE) {
                throw new BizException(2003, "题目答案格式非法");
            }
            for (Object cell : row) {
                if (!(cell instanceof Number number)) {
                    throw new BizException(2003, "题目答案格式非法");
                }
                flat.add(number.intValue());
            }
        }
        return flat;
    }

    /** 用户提交：9 行共 81 个数字，允许空格、逗号、分隔线等噪声；解析失败返回 null。 */
    static int[] parseSubmission(String submitted) {
        if (submitted == null) {
            return null;
        }
        int[] cells = new int[CELLS];
        int cursor = 0;
        for (int i = 0; i < submitted.length(); i++) {
            char ch = submitted.charAt(i);
            if (ch >= '1' && ch <= '9') {
                if (cursor >= CELLS) {
                    return null;
                }
                cells[cursor++] = ch - '0';
            } else if (ch == '0' || ch == '.' || ch == '_' || ch == '*') {
                if (cursor >= CELLS) {
                    return null;
                }
                cells[cursor++] = 0;
            }
        }
        return cursor == CELLS ? cells : null;
    }

    private static String flatten(List<Integer> solution) {
        StringBuilder builder = new StringBuilder(CELLS);
        for (int value : solution) {
            builder.append((char) ('0' + value));
        }
        return builder.toString();
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
