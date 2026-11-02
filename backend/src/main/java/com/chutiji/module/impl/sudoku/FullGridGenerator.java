package com.chutiji.module.impl.sudoku;

import java.util.Random;

/**
 * 终盘生成：随机化回溯填空，候选判断走位掩码。
 * 每行/每列/每宫各持一个 9 位掩码，第 d 位为 1 表示数字 d 已用。
 */
final class FullGridGenerator {

    private FullGridGenerator() {
    }

    /** 生成一张合法的完整终盘（81 格，取值 1-9）。 */
    static int[] fill(Random random) {
        int[] grid = new int[81];
        int[] rows = new int[9];
        int[] cols = new int[9];
        int[] boxes = new int[9];
        if (!place(grid, 0, rows, cols, boxes, random)) {
            throw new IllegalStateException("终盘生成失败");
        }
        return grid;
    }

    private static boolean place(int[] grid, int index, int[] rows, int[] cols, int[] boxes, Random random) {
        if (index == 81) {
            return true;
        }
        int row = index / 9;
        int col = index % 9;
        int box = (row / 3) * 3 + col / 3;
        int mask = ~(rows[row] | cols[col] | boxes[box]) & 0x1FF;

        int[] digits = new int[9];
        int count = 0;
        for (int digit = 1; digit <= 9; digit++) {
            if ((mask & (1 << (digit - 1))) != 0) {
                digits[count++] = digit;
            }
        }
        for (int i = count - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            int tmp = digits[i];
            digits[i] = digits[j];
            digits[j] = tmp;
        }

        for (int i = 0; i < count; i++) {
            int digit = digits[i];
            int bit = 1 << (digit - 1);
            grid[index] = digit;
            rows[row] |= bit;
            cols[col] |= bit;
            boxes[box] |= bit;
            if (place(grid, index + 1, rows, cols, boxes, random)) {
                return true;
            }
            grid[index] = 0;
            rows[row] &= ~bit;
            cols[col] &= ~bit;
            boxes[box] &= ~bit;
        }
        return false;
    }
}
