package com.chutiji.module.impl.sudoku;

/**
 * 测试专用独立求解器：朴素回溯 + 行/列/宫布尔表，刻意不复用生产实现，
 * 使得“唯一解验证”不会被生产代码里的同一个 bug 同时污染。
 */
final class IndependentSolver {

    private IndependentSolver() {
    }

    static int countSolutions(int[] grid, int limit) {
        boolean[][] rows = new boolean[9][10];
        boolean[][] cols = new boolean[9][10];
        boolean[][] boxes = new boolean[9][10];
        int[] work = grid.clone();
        for (int cell = 0; cell < 81; cell++) {
            int digit = work[cell];
            if (digit == 0) {
                continue;
            }
            int row = cell / 9;
            int col = cell % 9;
            int box = (row / 3) * 3 + col / 3;
            if (rows[row][digit] || cols[col][digit] || boxes[box][digit]) {
                return 0;
            }
            rows[row][digit] = true;
            cols[col][digit] = true;
            boxes[box][digit] = true;
        }
        return search(work, rows, cols, boxes, 0, limit);
    }

    private static int search(int[] grid, boolean[][] rows, boolean[][] cols, boolean[][] boxes, int cursor,
                              int limit) {
        int target = -1;
        for (int cell = cursor; cell < 81; cell++) {
            if (grid[cell] == 0) {
                target = cell;
                break;
            }
        }
        if (target < 0) {
            return 1;
        }
        int row = target / 9;
        int col = target % 9;
        int box = (row / 3) * 3 + col / 3;
        int found = 0;
        for (int digit = 1; digit <= 9; digit++) {
            if (rows[row][digit] || cols[col][digit] || boxes[box][digit]) {
                continue;
            }
            grid[target] = digit;
            rows[row][digit] = true;
            cols[col][digit] = true;
            boxes[box][digit] = true;
            found += search(grid, rows, cols, boxes, target + 1, limit - found);
            grid[target] = 0;
            rows[row][digit] = false;
            cols[col][digit] = false;
            boxes[box][digit] = false;
            if (found >= limit) {
                return found;
            }
        }
        return found;
    }
}
