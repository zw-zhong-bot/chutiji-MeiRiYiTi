package com.chutiji.module.impl.sudoku;

/** 解数统计：回溯 + 最少候选格优先，数到 limit 就停，用于唯一解判定。 */
final class SolutionCounter {

    private SolutionCounter() {
    }

    /** 统计解的数量，最多数到 limit 个。 */
    static int count(int[] grid, int limit) {
        int[] rows = new int[9];
        int[] cols = new int[9];
        int[] boxes = new int[9];
        int[] work = grid.clone();
        for (int i = 0; i < 81; i++) {
            int digit = work[i];
            if (digit == 0) {
                continue;
            }
            int bit = 1 << (digit - 1);
            if ((rows[i / 9] & bit) != 0 || (cols[i % 9] & bit) != 0
                    || (boxes[(i / 9 / 3) * 3 + i % 9 / 3] & bit) != 0) {
                return 0;
            }
            rows[i / 9] |= bit;
            cols[i % 9] |= bit;
            boxes[(i / 9 / 3) * 3 + i % 9 / 3] |= bit;
        }
        return search(work, rows, cols, boxes, limit);
    }

    static boolean isUnique(int[] grid) {
        return count(grid, 2) == 1;
    }

    private static int search(int[] grid, int[] rows, int[] cols, int[] boxes, int limit) {
        int target = -1;
        int chosen = 0;
        int fewest = 10;
        for (int i = 0; i < 81; i++) {
            if (grid[i] != 0) {
                continue;
            }
            int box = (i / 9 / 3) * 3 + i % 9 / 3;
            int mask = ~(rows[i / 9] | cols[i % 9] | boxes[box]) & 0x1FF;
            int size = Integer.bitCount(mask);
            if (size == 0) {
                return 0;
            }
            if (size < fewest) {
                fewest = size;
                target = i;
                chosen = mask;
                if (size == 1) {
                    break;
                }
            }
        }
        if (target < 0) {
            return 1;
        }
        int row = target / 9;
        int col = target % 9;
        int box = (row / 3) * 3 + col / 3;
        int found = 0;
        int mask = chosen;
        while (mask != 0) {
            int bit = mask & -mask;
            mask ^= bit;
            grid[target] = Integer.numberOfTrailingZeros(bit) + 1;
            rows[row] |= bit;
            cols[col] |= bit;
            boxes[box] |= bit;
            found += search(grid, rows, cols, boxes, limit - found);
            grid[target] = 0;
            rows[row] &= ~bit;
            cols[col] &= ~bit;
            boxes[box] &= ~bit;
            if (found >= limit) {
                return found;
            }
        }
        return found;
    }
}
