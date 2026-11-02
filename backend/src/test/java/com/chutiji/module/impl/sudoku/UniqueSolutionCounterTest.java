package com.chutiji.module.impl.sudoku;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class UniqueSolutionCounterTest {

    /** 经典 8 宫格示例，唯一解。 */
    private static final String UNIQUE_PUZZLE = ""
            + "530070000"
            + "600195000"
            + "098000060"
            + "800060003"
            + "400803001"
            + "700020006"
            + "060000280"
            + "000419005"
            + "000080079";

    @Test
    @DisplayName("唯一解题目数出 1 个解")
    void countsUnique() {
        int[] puzzle = parse(UNIQUE_PUZZLE);
        assertEquals(1, SolutionCounter.count(puzzle, 2));
        assertTrue(SolutionCounter.isUnique(puzzle));
    }

    @Test
    @DisplayName("挖掉关键格后不再是唯一解")
    void detectsMultipleSolutions() {
        int[] broken = parse(UNIQUE_PUZZLE);
        broken[0] = 0;
        broken[1] = 0;
        broken[2] = 0;
        broken[9] = 0;
        broken[10] = 0;
        broken[11] = 0;
        assertFalse(SolutionCounter.isUnique(broken));
        assertEquals(2, SolutionCounter.count(broken, 2));
    }

    @Test
    @DisplayName("空格越界（无候选）判 0 解")
    void detectsDeadEnd() {
        int[] grid = parse(UNIQUE_PUZZLE);
        // 第 0 行第 0 列改成 1 会和第 0 行已有的 1 冲突
        grid[0] = 1;
        assertEquals(0, SolutionCounter.count(grid, 2));
    }

    @Test
    @DisplayName("终盘本身就是唯一解")
    void solvedGridIsUnique() {
        int[] solved = FullGridGenerator.fill(new java.util.Random(5L));
        assertEquals(1, SolutionCounter.count(solved, 2));
    }

    private int[] parse(String text) {
        int[] grid = new int[81];
        for (int i = 0; i < 81; i++) {
            grid[i] = text.charAt(i) - '0';
        }
        return grid;
    }
}
