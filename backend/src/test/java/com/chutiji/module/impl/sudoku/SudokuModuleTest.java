package com.chutiji.module.impl.sudoku;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.chutiji.module.api.AxisKind;
import com.chutiji.module.api.DifficultyAxis;
import com.chutiji.module.api.GenRequest;
import com.chutiji.module.api.GeneratedQuestion;
import com.chutiji.module.api.GradeRequest;
import com.chutiji.module.api.GradeResult;
import com.chutiji.module.api.ModuleMeta;
import com.chutiji.module.api.QuestionType;

class SudokuModuleTest {

    private final SudokuModule module = new SudokuModule();

    @Test
    @DisplayName("meta：key=sudoku、type=BOARD、两根轴 ENUM + BOOLEAN")
    void metaDeclaresAxes() {
        ModuleMeta meta = module.meta();
        assertEquals("sudoku", meta.key());
        assertEquals("数独", meta.name());
        assertEquals(QuestionType.BOARD, meta.type());
        assertEquals(2, meta.axes().size());

        DifficultyAxis technique = meta.axes().get(0);
        assertEquals("techniqueLevel", technique.key());
        assertEquals(AxisKind.ENUM, technique.kind());
        assertEquals("EASY", technique.defaultValue());
        assertNotNull(technique.options());
        List<String> codes = technique.options().stream().map(o -> o.value()).toList();
        assertEquals(List.of("EASY", "MEDIUM", "HARD", "EXPERT"), codes);

        DifficultyAxis symmetry = meta.axes().get(1);
        assertEquals("symmetry", symmetry.key());
        assertEquals(AxisKind.BOOLEAN, symmetry.kind());
        assertEquals(Boolean.TRUE, symmetry.defaultValue());
    }

    @Test
    @DisplayName("payload 只有 size/grid/answerFormat，绝不含 solution")
    void payloadHasNoSolution() {
        GeneratedQuestion q = module.generate(new GenRequest("sudoku", Map.of("techniqueLevel", "EASY")));
        assertEquals("数独（对称挖洞）", q.prompt());
        assertEquals(3, q.payload().size());
        assertEquals(9, q.payload().get("size"));
        assertEquals("GRID", q.payload().get("answerFormat"));
        assertFalse(q.payload().containsKey("solution"));
        assertFalse(q.payload().containsKey("answer"));
        assertTrue(q.answer().containsKey("solution"));
    }

    @Test
    @DisplayName("对称开关影响题面")
    void symmetrySwitchChangesPrompt() {
        GeneratedQuestion symmetric = module.generate(new GenRequest("sudoku",
                Map.of("techniqueLevel", "EASY", "symmetry", true)));
        GeneratedQuestion plain = module.generate(new GenRequest("sudoku",
                Map.of("techniqueLevel", "EASY", "symmetry", false)));
        assertEquals("数独（对称挖洞）", symmetric.prompt());
        assertEquals("数独", plain.prompt());
    }

    @Test
    @DisplayName("EASY 100 道：每道都用独立求解器验证恰好唯一解")
    void hundredEasyPuzzlesAllUnique() {
        assertAllUnique("EASY", 100);
    }

    @Test
    @DisplayName("MEDIUM 20 道 / HARD 10 道：每道都用独立求解器验证恰好唯一解")
    void mediumAndHardPuzzlesUnique() {
        assertAllUnique("MEDIUM", 20);
        assertAllUnique("HARD", 10);
    }

    @Test
    @DisplayName("单题完整生成耗时远低于 300ms（20 次采样取最大值）")
    void singleGenerationIsFast() {
        String[] levels = {"EASY", "MEDIUM", "HARD", "EXPERT"};
        for (int warmup = 0; warmup < 8; warmup++) {
            module.generate(new GenRequest("sudoku",
                    Map.of("techniqueLevel", levels[warmup % levels.length], "symmetry", true)));
        }

        long worst = 0;
        long total = 0;
        int samples = 20;
        for (int i = 0; i < samples; i++) {
            String level = levels[i % levels.length];
            long start = System.nanoTime();
            module.generate(new GenRequest("sudoku", Map.of("techniqueLevel", level, "symmetry", true)));
            long elapsedMs = (System.nanoTime() - start) / 1_000_000;
            worst = Math.max(worst, elapsedMs);
            total += elapsedMs;
        }
        System.out.printf("[性能] %d 次采样 最大 %d ms，平均 %.2f ms%n", samples, worst,
                (double) total / samples);
        assertTrue(worst < 300, "单题生成最大耗时 " + worst + "ms，超过 300ms 目标");
    }

    @Test
    @DisplayName("判题：全对判对；改一格判错并指出第一处错误位置")
    void gradesCorrectly() {
        GeneratedQuestion q = module.generate(new GenRequest("sudoku", Map.of("techniqueLevel", "EASY")));
        String solution = SudokuTestSupport.flatten(q.answer());

        GradeResult right = module.grade(new GradeRequest("sudoku", q.answer(), solution));
        assertTrue(right.correct());
        assertEquals(solution, right.correctAnswer());

        int cell = 3 * 9 + 6;
        int original = solution.charAt(cell) - '0';
        int wrongDigit = original % 9 + 1;
        String tampered = solution.substring(0, cell) + wrongDigit + solution.substring(cell + 1);
        GradeResult wrong = module.grade(new GradeRequest("sudoku", q.answer(), tampered));
        assertFalse(wrong.correct());
        assertEquals(solution, wrong.correctAnswer());
        assertTrue(wrong.explanation().contains("第 4 行第 7 列"),
                "应指出第一处错误位置，实际: " + wrong.explanation());

        String spaced = String.join(" ", solution.split(""));
        GradeResult formatted = module.grade(new GradeRequest("sudoku", q.answer(), spaced));
        assertTrue(formatted.correct(), "带格式噪声的正确答案应判对: " + formatted.explanation());
    }

    @Test
    @DisplayName("判题：提交长度不足判错而不是抛异常")
    void rejectsShortSubmission() {
        GeneratedQuestion q = module.generate(new GenRequest("sudoku", Map.of("techniqueLevel", "EASY")));
        GradeResult result = module.grade(new GradeRequest("sudoku", q.answer(), "123"));
        assertFalse(result.correct());
        assertTrue(result.explanation().contains("格式"));
    }

    @Test
    @DisplayName("给定格的数字在挖洞后保持不变，题面是解的子集")
    void givensMatchSolution() {
        for (int round = 0; round < 20; round++) {
            GeneratedQuestion q = module.generate(new GenRequest("sudoku",
                    Map.of("techniqueLevel", "EASY")));
            int[][] grid = SudokuTestSupport.toMatrix(q.payload().get("grid"));
            int[][] solution = SudokuTestSupport.toMatrix(q.answer().get("solution"));
            for (int row = 0; row < 9; row++) {
                for (int column = 0; column < 9; column++) {
                    if (grid[row][column] != 0) {
                        assertEquals(solution[row][column], grid[row][column],
                                "第 " + (row + 1) + " 行第 " + (column + 1) + " 列给定数与解不一致");
                    }
                }
            }
        }
    }

    @Test
    @DisplayName("对称挖洞：开启时每道题的题面 180 度对称")
    void symmetricDigging() {
        for (int round = 0; round < 10; round++) {
            GeneratedQuestion q = module.generate(new GenRequest("sudoku",
                    Map.of("techniqueLevel", "EASY", "symmetry", true)));
            int[][] grid = SudokuTestSupport.toMatrix(q.payload().get("grid"));
            for (int row = 0; row < 9; row++) {
                for (int column = 0; column < 9; column++) {
                    boolean here = grid[row][column] != 0;
                    boolean opposite = grid[8 - row][8 - column] != 0;
                    assertEquals(opposite, here,
                            "第 " + (row + 1) + " 行第 " + (column + 1) + " 列不对称");
                }
            }
        }
    }

    private void assertAllUnique(String level, int count) {
        assertTrue(count > 0);
        for (int i = 0; i < count; i++) {
            GeneratedQuestion q = module.generate(new GenRequest("sudoku",
                    Map.of("techniqueLevel", level, "symmetry", true)));
            int[][] grid = SudokuTestSupport.toMatrix(q.payload().get("grid"));
            int[] flat = new int[81];
            for (int row = 0; row < 9; row++) {
                for (int column = 0; column < 9; column++) {
                    flat[row * 9 + column] = grid[row][column];
                }
            }
            // 独立求解器（朴素回溯，不复用生产实现）验证恰好一个解
            int solutions = IndependentSolver.countSolutions(flat, 2);
            assertEquals(1, solutions, level + " 第 " + (i + 1) + " 题不是唯一解，解数=" + solutions);
        }
    }
}
