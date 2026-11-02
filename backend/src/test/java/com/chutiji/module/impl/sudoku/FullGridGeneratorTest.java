package com.chutiji.module.impl.sudoku;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Random;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class FullGridGeneratorTest {

    @Test
    @DisplayName("终盘的每行每列每宫都是 1-9 各一次")
    void fullGridIsValid() {
        Random random = new Random(2026L);
        for (int round = 0; round < 50; round++) {
            int[] grid = FullGridGenerator.fill(random);
            assertEquals(81, grid.length);
            for (int i = 0; i < 81; i++) {
                assertTrue(grid[i] >= 1 && grid[i] <= 9, "第 " + i + " 格越界: " + grid[i]);
            }
            for (int unit = 0; unit < 9; unit++) {
                assertUnitComplete(row(grid, unit), "行 " + unit);
                assertUnitComplete(col(grid, unit), "列 " + unit);
                assertUnitComplete(box(grid, unit), "宫 " + unit);
            }
        }
    }

    @Test
    @DisplayName("终盘随机化生效：连续两张不完全相同")
    void randomized() {
        Random random = new Random(7L);
        int identical = 0;
        int[] previous = FullGridGenerator.fill(random);
        for (int round = 0; round < 20; round++) {
            int[] next = FullGridGenerator.fill(random);
            if (Arrays.equals(previous, next)) {
                identical++;
            }
            previous = next;
        }
        assertTrue(identical < 2, "终盘几乎总是一样，随机化失效: " + identical);
    }

    @Test
    @DisplayName("终盘生成平均耗时远低于 50ms")
    void fast() {
        Random random = new Random(99L);
        for (int warmup = 0; warmup < 50; warmup++) {
            FullGridGenerator.fill(random);
        }
        long start = System.nanoTime();
        int rounds = 200;
        for (int i = 0; i < rounds; i++) {
            FullGridGenerator.fill(random);
        }
        double averageMs = (System.nanoTime() - start) / 1_000_000.0 / rounds;
        assertTrue(averageMs < 50, "终盘生成平均耗时 " + averageMs + "ms，超过 50ms 目标");
        System.out.printf("[终盘] %d 次平均 %.3f ms%n", rounds, averageMs);
    }

    private void assertUnitComplete(int[] cells, String unitName) {
        boolean[] seen = new boolean[10];
        for (int value : cells) {
            assertFalse(seen[value], unitName + " 出现重复数字 " + value);
            seen[value] = true;
        }
        for (int digit = 1; digit <= 9; digit++) {
            assertTrue(seen[digit], unitName + " 缺少数字 " + digit);
        }
    }

    private int[] row(int[] grid, int index) {
        return Arrays.copyOfRange(grid, index * 9, index * 9 + 9);
    }

    private int[] col(int[] grid, int index) {
        int[] cells = new int[9];
        for (int r = 0; r < 9; r++) {
            cells[r] = grid[r * 9 + index];
        }
        return cells;
    }

    private int[] box(int[] grid, int index) {
        int[] cells = new int[9];
        int startRow = (index / 3) * 3;
        int startCol = (index % 3) * 3;
        int cursor = 0;
        for (int r = startRow; r < startRow + 3; r++) {
            for (int c = startCol; c < startCol + 3; c++) {
                cells[cursor++] = grid[r * 9 + c];
            }
        }
        return cells;
    }
}
