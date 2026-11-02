package com.chutiji.module.impl.logic;

import java.util.ArrayList;
import java.util.List;

/**
 * 穷举排列求解器：给一组条件，算出全部满足条件的排列。
 * 搜索时对「涉及的人已经全部确定位置」的条件提前剪枝，结果与全量穷举一致。
 */
final class PermutationSolver {

    private PermutationSolver() {
    }

    /** 所有满足条件的排列，每条满足 positions[人物] = 位置。 */
    static List<int[]> solve(int people, List<Condition> conditions) {
        List<int[]> solutions = new ArrayList<>();
        int[] positions = new int[people];
        boolean[] used = new boolean[people];
        search(0, positions, used, conditions, solutions);
        return solutions;
    }

    /** 只关心解的数量时用它：找到 limit 个就停。 */
    static int countUpTo(int people, List<Condition> conditions, int limit) {
        int[] found = {0};
        int[] positions = new int[people];
        boolean[] used = new boolean[people];
        count(0, positions, used, conditions, found, limit);
        return found[0];
    }

    /** n! —— 判断条件数量是否足以区分排列的量级参考。 */
    static int permutationCount(int people) {
        int total = 1;
        for (int i = 2; i <= people; i++) {
            total *= i;
        }
        return total;
    }

    private static void search(int person, int[] positions, boolean[] used,
            List<Condition> conditions, List<int[]> solutions) {
        if (person == positions.length) {
            if (satisfiesAll(conditions, positions)) {
                solutions.add(positions.clone());
            }
            return;
        }
        for (int position = 0; position < positions.length; position++) {
            if (used[position]) {
                continue;
            }
            positions[person] = position;
            used[position] = true;
            if (stillPossible(person, positions, conditions)) {
                search(person + 1, positions, used, conditions, solutions);
            }
            used[position] = false;
        }
    }

    private static void count(int person, int[] positions, boolean[] used,
            List<Condition> conditions, int[] found, int limit) {
        if (found[0] >= limit) {
            return;
        }
        if (person == positions.length) {
            if (satisfiesAll(conditions, positions)) {
                found[0]++;
            }
            return;
        }
        for (int position = 0; position < positions.length; position++) {
            if (used[position]) {
                continue;
            }
            positions[person] = position;
            used[position] = true;
            if (stillPossible(person, positions, conditions)) {
                count(person + 1, positions, used, conditions, found, limit);
            }
            used[position] = false;
        }
    }

    private static boolean satisfiesAll(List<Condition> conditions, int[] positions) {
        for (Condition condition : conditions) {
            if (!condition.test(positions)) {
                return false;
            }
        }
        return true;
    }

    /** 索引 0..assignedUpTo 的人已定位置；只判定「涉及的人全部已定」的条件。 */
    private static boolean stillPossible(int assignedUpTo, int[] positions, List<Condition> conditions) {
        for (Condition condition : conditions) {
            if (maxPerson(condition) <= assignedUpTo && !condition.test(positions)) {
                return false;
            }
        }
        return true;
    }

    static int maxPerson(Condition condition) {
        return switch (condition) {
            case Condition.Adjacent c -> Math.max(c.i(), c.j());
            case Condition.LeftOf c -> Math.max(c.i(), c.j());
            case Condition.ImmediatelyLeftOf c -> Math.max(c.i(), c.j());
            case Condition.AtPosition c -> c.i();
            case Condition.NotAtEnd c -> c.i();
            case Condition.Gap c -> Math.max(c.i(), c.j());
        };
    }
}
