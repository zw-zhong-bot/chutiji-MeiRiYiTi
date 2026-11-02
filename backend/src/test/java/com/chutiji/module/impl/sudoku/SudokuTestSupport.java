package com.chutiji.module.impl.sudoku;

import java.util.List;
import java.util.Map;

/** 测试辅助：把 payload/answer 里的嵌套结构读回矩阵。 */
final class SudokuTestSupport {

    private SudokuTestSupport() {
    }

    @SuppressWarnings("unchecked")
    static int[][] toMatrix(Object rawGrid) {
        List<List<Integer>> rows = (List<List<Integer>>) rawGrid;
        int[][] matrix = new int[9][9];
        for (int row = 0; row < 9; row++) {
            List<Integer> line = rows.get(row);
            for (int column = 0; column < 9; column++) {
                matrix[row][column] = line.get(column);
            }
        }
        return matrix;
    }

    static int[][] toMatrix2(Object rawGrid) {
        return toMatrix(rawGrid);
    }

    @SuppressWarnings("unchecked")
    static String flatten(Map<String, Object> answer) {
        List<List<Integer>> rows = (List<List<Integer>>) answer.get("solution");
        StringBuilder builder = new StringBuilder(81);
        for (List<Integer> row : rows) {
            for (Integer value : row) {
                builder.append((char) ('0' + value));
            }
        }
        return builder.toString();
    }

    @SuppressWarnings("unchecked")
    static String flattenGrid(Map<String, Object> payload) {
        List<List<Integer>> rows = (List<List<Integer>>) payload.get("grid");
        StringBuilder builder = new StringBuilder(81);
        for (List<Integer> row : rows) {
            for (Integer value : row) {
                builder.append((char) ('0' + value));
            }
        }
        return builder.toString();
    }
}
