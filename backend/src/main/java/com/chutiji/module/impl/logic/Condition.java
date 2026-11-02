package com.chutiji.module.impl.logic;

/**
 * 一条条件约束。全部条件都作用在同一个排列上：
 * positions[人物下标] = 位置下标（0 为最左/一号位）。
 */
sealed interface Condition {

    /** 该条件在给定排列下是否成立。 */
    boolean test(int[] positions);

    /** 条件的自然语言描述。 */
    String describe(String[] people);

    /** i 与 j 相邻。 */
    record Adjacent(int i, int j) implements Condition {
        @Override
        public boolean test(int[] positions) {
            return Math.abs(positions[i] - positions[j]) == 1;
        }

        @Override
        public String describe(String[] people) {
            return people[i] + "和" + people[j] + "紧挨着";
        }
    }

    /** i 在 j 的左边（不一定相邻）。 */
    record LeftOf(int i, int j) implements Condition {
        @Override
        public boolean test(int[] positions) {
            return positions[i] < positions[j];
        }

        @Override
        public String describe(String[] people) {
            return people[i] + "在" + people[j] + "的左边";
        }
    }

    /** i 在 j 的紧左边。 */
    record ImmediatelyLeftOf(int i, int j) implements Condition {
        @Override
        public boolean test(int[] positions) {
            return positions[j] - positions[i] == 1;
        }

        @Override
        public String describe(String[] people) {
            return people[i] + "紧挨着" + people[j] + "的左边";
        }
    }

    /** i 恰好站在第 position 号位（0 起）。 */
    record AtPosition(int i, int position) implements Condition {
        @Override
        public boolean test(int[] positions) {
            return positions[i] == position;
        }

        @Override
        public String describe(String[] people) {
            return people[i] + "站在第" + (position + 1) + "个位置";
        }
    }

    /** i 不在两端。 */
    record NotAtEnd(int i) implements Condition {
        @Override
        public boolean test(int[] positions) {
            return positions[i] > 0 && positions[i] < positions.length - 1;
        }

        @Override
        public String describe(String[] people) {
            return people[i] + "不在两端";
        }
    }

    /** i 与 j 之间恰好隔 gap 个人。 */
    record Gap(int i, int j, int gap) implements Condition {
        @Override
        public boolean test(int[] positions) {
            return Math.abs(positions[i] - positions[j]) == gap + 1;
        }

        @Override
        public String describe(String[] people) {
            return people[i] + "和" + people[j] + "之间隔着" + gap + "个人";
        }
    }
}
