package com.chutiji.module.impl.sudoku;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.Random;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.chutiji.module.api.GenRequest;
import com.chutiji.module.api.GeneratedQuestion;

class TechniqueSolverTest {

    @Test
    @DisplayName("只用 EASY 技巧解不出需要数对的题：等级上限生效")
    void levelCapIsEnforced() {
        Random random = new Random(31337L);
        int cappedOut = 0;
        for (int i = 0; i < 5; i++) {
            SudokuGenerator.Puzzle puzzle = SudokuGenerator.generate(random, TechniqueLevel.EXPERT, true, 20);
            if (puzzle.level().ordinal() < TechniqueLevel.MEDIUM.ordinal()) {
                continue;
            }
            TechniqueSolver.Result solved = TechniqueSolver.solve(puzzle.givens(), TechniqueLevel.EASY);
            if (!solved.solved()) {
                cappedOut++;
            }
        }
        assertTrue(cappedOut > 0, "高等级题在 EASY 技巧下应当解不出，等级上限没有生效");
    }

    @Test
    @DisplayName("终盘用任何等级都能解出（无空格即已完成）")
    void solvedGridNeedsNoTechnique() {
        int[] grid = FullGridGenerator.fill(new Random(11L));
        for (TechniqueLevel level : TechniqueLevel.values()) {
            TechniqueSolver.Result result = TechniqueSolver.solve(grid, level);
            assertTrue(result.solved());
            assertEquals(TechniqueLevel.EASY, result.level());
        }
    }

    @Test
    @DisplayName("EASY 题面用 EASY 技巧即可解出，且解与 answer 一致")
    void easyPuzzleSolvableAtEasyLevel() {
        SudokuModule module = new SudokuModule();
        for (int i = 0; i < 10; i++) {
            GeneratedQuestion q = module.generate(new GenRequest("sudoku", Map.of("techniqueLevel", "EASY")));
            int[][] grid = SudokuTestSupport.toMatrix(q.payload().get("grid"));
            int[][] solution = SudokuTestSupport.toMatrix(q.answer().get("solution"));
            int[] flat = new int[81];
            for (int row = 0; row < 9; row++) {
                for (int column = 0; column < 9; column++) {
                    flat[row * 9 + column] = grid[row][column];
                }
            }
            TechniqueSolver.Result result = TechniqueSolver.solve(flat, TechniqueLevel.EASY);
            assertTrue(result.solved(), "EASY 题第 " + (i + 1) + " 道用 EASY 技巧解不出");
            assertEquals(TechniqueLevel.EASY, result.level());
            assertEquals(1, IndependentSolver.countSolutions(flat, 2));
            assertEquals(81, SudokuTestSupport.flatten(q.answer()).length());
            assertEquals(9, solution.length);
        }
    }

    @Test
    @DisplayName("矛盾题面判为解不出")
    void contradictoryPuzzle() {
        int[] grid = new int[81];
        grid[0] = 5;
        grid[1] = 5;
        TechniqueSolver.Result result = TechniqueSolver.solve(grid, TechniqueLevel.EXPERT);
        assertFalse(result.solved());
    }
}
