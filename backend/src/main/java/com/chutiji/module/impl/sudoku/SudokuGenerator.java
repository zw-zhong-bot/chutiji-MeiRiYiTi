package com.chutiji.module.impl.sudoku;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * 数独生成：填终盘 -> 挖洞（每挖一次验证唯一解，并按目标技巧等级封顶）-> 评级。
 * 挖洞对称时按 180 度旋转成对挖。
 */
final class SudokuGenerator {

    /** 一道题：题目（0 为空格）、完整解、实际技巧等级、尝试次数。 */
    record Puzzle(int[] givens, int[] solution, TechniqueLevel level, int attempts) {
    }

    private SudokuGenerator() {
    }

    /** 单题生成的时间预算：超过就停止重试，用已拿到的最好一题，保证整体远低于 300ms。 */
    static final long DEFAULT_BUDGET_NANOS = 200_000_000L;

    /**
     * 生成一道不高于 targetLevel 的题：评级恰好等于 targetLevel 时直接返回；
     * 次数或时间预算用尽时返回等级最高的那一题（可能低于目标）。
     */
    static Puzzle generate(Random random, TechniqueLevel targetLevel, boolean symmetric, int maxAttempts) {
        return generate(random, targetLevel, symmetric, maxAttempts, DEFAULT_BUDGET_NANOS);
    }

    static Puzzle generate(Random random, TechniqueLevel targetLevel, boolean symmetric, int maxAttempts,
                           long budgetNanos) {
        long deadline = System.nanoTime() + budgetNanos;
        Puzzle best = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            Puzzle candidate = attempt(random, targetLevel, symmetric, attempt);
            if (candidate.level() == targetLevel) {
                return candidate;
            }
            if (best == null || candidate.level().ordinal() > best.level().ordinal()) {
                best = candidate;
            }
            if (System.nanoTime() >= deadline) {
                return best;
            }
        }
        return best;
    }

    private static Puzzle attempt(Random random, TechniqueLevel targetLevel, boolean symmetric, int attempt) {
        int[] solution = FullGridGenerator.fill(random);
        int[] puzzle = solution.clone();

        List<Integer> order = new ArrayList<>(81);
        for (int cell = 0; cell < 81; cell++) {
            order.add(cell);
        }

        // 反复整轮扫描直到一整轮都挖不动，避免单轮贪心过早停在稠密题面。
        boolean progress = true;
        while (progress) {
            progress = false;
            Collections.shuffle(order, random);
            for (int cell : order) {
                int mirror = symmetric ? 80 - cell : cell;
                int savedCell = puzzle[cell];
                int savedMirror = puzzle[mirror];
                if (savedCell == 0) {
                    continue;
                }
                puzzle[cell] = 0;
                puzzle[mirror] = 0;
                if (!SolutionCounter.isUnique(puzzle) || !withinLevel(puzzle, targetLevel)) {
                    puzzle[cell] = savedCell;
                    puzzle[mirror] = savedMirror;
                } else {
                    progress = true;
                }
            }
        }

        TechniqueLevel level = TechniqueSolver.solve(puzzle, TechniqueLevel.EXPERT).level();
        if (level == null) {
            level = TechniqueLevel.EXPERT;
        }
        if (level.ordinal() > targetLevel.ordinal()) {
            // 封顶逻辑理论上不会走到这里，兜底回落到最保守的等级标记
            level = targetLevel;
        }
        return new Puzzle(puzzle, solution, level, attempt);
    }

    /** 该题是否仍能用不超过 targetLevel 的技巧求解。 */
    private static boolean withinLevel(int[] puzzle, TechniqueLevel targetLevel) {
        return TechniqueSolver.solve(puzzle, targetLevel).solved();
    }
}
