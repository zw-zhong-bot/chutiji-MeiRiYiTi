package com.chutiji.module.impl.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PermutationSolverTest {

    @Test
    @DisplayName("无条件时解数 = 4! = 24")
    void noConditionMeansAllPermutations() {
        assertEquals(24, PermutationSolver.countUpTo(4, List.of(), 100));
        assertEquals(24, PermutationSolver.permutationCount(4));
        assertEquals(2, PermutationSolver.permutationCount(2));
    }

    @Test
    @DisplayName("单条件解数符合组合直觉：定位 6、左右序 12、不在两端 12、相邻 12")
    void singleConditionCounts() {
        assertEquals(6, PermutationSolver.countUpTo(4,
                List.of(new Condition.AtPosition(0, 0)), 100));
        assertEquals(12, PermutationSolver.countUpTo(4,
                List.of(new Condition.LeftOf(0, 1)), 100));
        assertEquals(12, PermutationSolver.countUpTo(4,
                List.of(new Condition.NotAtEnd(0)), 100));
        assertEquals(12, PermutationSolver.countUpTo(4,
                List.of(new Condition.Adjacent(0, 1)), 100));
    }

    @Test
    @DisplayName("两组条件互相矛盾时无解")
    void contradictoryConditions() {
        assertEquals(0, PermutationSolver.countUpTo(4, List.of(
                new Condition.AtPosition(0, 0),
                new Condition.AtPosition(1, 0)), 100));
    }

    @Test
    @DisplayName("充分条件下唯一解，且解满足全部条件")
    void tightConditionsGiveUniqueSolution() {
        List<Condition> conditions = List.of(
                new Condition.AtPosition(0, 0),
                new Condition.AtPosition(1, 1),
                new Condition.AtPosition(2, 2));
        List<int[]> solutions = PermutationSolver.solve(4, conditions);
        assertEquals(1, solutions.size());
        int[] only = solutions.get(0);
        assertEquals(0, only[0]);
        assertEquals(1, only[1]);
        assertEquals(2, only[2]);
        assertEquals(3, only[3]);
    }

    @Test
    @DisplayName("剪枝不改变结果：solve 与 countUpTo 对同一组条件给出一致解数")
    void pruningKeepsResultConsistent() {
        List<Condition> conditions = List.of(
                new Condition.Adjacent(0, 1),
                new Condition.LeftOf(2, 3),
                new Condition.NotAtEnd(0));
        int counted = PermutationSolver.countUpTo(4, conditions, 100);
        int solved = PermutationSolver.solve(4, conditions).size();
        assertEquals(counted, solved);
        for (int[] solution : PermutationSolver.solve(4, conditions)) {
            for (Condition condition : conditions) {
                assertTrue(condition.test(solution), "解不满足条件: " + condition.describe(
                        new String[] {"甲", "乙", "丙", "丁"}));
            }
        }
    }

    @Test
    @DisplayName("2 到 5 人的解数都等于穷举的排列数")
    void solverScalesWithPeopleCount() {
        assertEquals(2, PermutationSolver.countUpTo(2, List.of(), 100));
        assertEquals(6, PermutationSolver.countUpTo(3, List.of(), 100));
        assertEquals(24, PermutationSolver.countUpTo(4, List.of(), 100));
        assertEquals(120, PermutationSolver.countUpTo(5, List.of(), 200));
    }

    @Test
    @DisplayName("条件描述文字稳定，不会出现空串或 null")
    void describesEveryCondition() {
        String[] people = {"甲", "乙", "丙", "丁"};
        List<Condition> conditions = new ArrayList<>(List.of(
                new Condition.Adjacent(0, 1),
                new Condition.LeftOf(0, 1),
                new Condition.ImmediatelyLeftOf(0, 1),
                new Condition.AtPosition(0, 0),
                new Condition.NotAtEnd(0),
                new Condition.Gap(0, 1, 2)));
        for (Condition condition : conditions) {
            String text = condition.describe(people);
            assertTrue(text != null && !text.isBlank());
            assertTrue(text.contains("甲"), "描述里应包含人物: " + text);
        }
    }
}
