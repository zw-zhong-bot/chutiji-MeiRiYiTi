package com.chutiji.module.impl.logic;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import com.chutiji.common.BizException;

/**
 * 逻辑推理生成器：先随机定一个排列作答案，再从「对该排列成立」的条件池里抽条件，
 * 每抽一条都用穷举重新数解，直到恰好唯一解。
 *
 * 条件数轴 conditionCount 是目标条数；4 人 4 位至少要 3 条条件才可能唯一确定，
 * 所以实际条数取 max(conditionCount, 3)，必要时继续加条件而不是交出一道多解题。
 */
final class LogicGenerator {

    /** 人物数量；4 人对应四个选项（正确答案 + 三个干扰项）。 */
    static final int PEOPLE = 4;

    /** 一轮生成最多重试多少轮（每轮换一组人物、目标排列与问句位置）。 */
    static final int MAX_ROUNDS = 60;

    /** 每轮内换几组条件试试。 */
    static final int ATTEMPTS_PER_ROUND = 8;

    /** 4 人 4 位唯一确定排列所需的最少条件数（实测 2 条永远不可能唯一）。 */
    static final int MIN_CONDITIONS_FOR_UNIQUE = 3;

    private static final String[] NAME_POOL = {
            "张三", "李四", "王五", "赵六", "钱七", "孙八", "周九", "吴十",
            "小明", "小红", "小刚", "小美"};

    /** 一次生成的结果，测试可直接检查条件与唯一解。 */
    record Puzzle(String[] people, int[] positions, List<Condition> conditions,
            int queryPosition, List<String> options, String correctOption,
            String prompt, String explanation) {
    }

    private final Random random;

    LogicGenerator(Random random) {
        this.random = random;
    }

    /**
     * 严格按请求的条数出题：4 人 4 位至少要 3 条条件才可能唯一确定，
     * 因此目标条数 = max(请求条数, 3)；一旦条数定下就不再增加，
     * 抽到的这组条件不唯一就整组换掉重来，绝不用「多给两条」凑唯一解。
     */
    Puzzle generate(int conditionCount, int stepDepth) {
        int targetCount = Math.max(MIN_CONDITIONS_FOR_UNIQUE, Math.min(6, conditionCount));
        for (int round = 0; round < MAX_ROUNDS; round++) {
            String[] people = pickPeople();
            int[] positions = randomPermutation();
            int queryPosition = random.nextInt(PEOPLE);
            int queriedPerson = personAt(positions, queryPosition);

            List<Condition> pool = candidatePool(people, positions, queriedPerson, stepDepth);
            if (pool.size() < targetCount) {
                continue;
            }
            for (int attempt = 0; attempt < ATTEMPTS_PER_ROUND; attempt++) {
                Collections.shuffle(pool, random);
                List<Condition> chosen = new ArrayList<>(pool.subList(0, targetCount));
                if (PermutationSolver.countUpTo(PEOPLE, chosen, 2) == 1) {
                    return build(people, positions, chosen, queryPosition);
                }
            }
        }
        throw new BizException(2007, "逻辑推理出题失败：重试 " + MAX_ROUNDS + " 轮仍未得到唯一解");
    }

    /**
     * 对目标排列成立、且不直接泄露问句答案的条件池。
     * stepDepth 决定可用条件类型：越深越依赖串联推理而不是直接给定位置。
     */
    private List<Condition> candidatePool(String[] people, int[] positions, int queriedPerson, int stepDepth) {
        List<Condition> advanced = new ArrayList<>();
        List<Condition> simple = new ArrayList<>();
        int depth = Math.max(1, Math.min(4, stepDepth));

        for (int i = 0; i < PEOPLE; i++) {
            // 被问的人不能直接由「站在第几个位置」给定，否则题目没有推理含量
            if (i != queriedPerson) {
                simple.add(new Condition.AtPosition(i, positions[i]));
            }
            if (positions[i] != 0 && positions[i] != PEOPLE - 1) {
                advanced.add(new Condition.NotAtEnd(i));
            }
            for (int j = i + 1; j < PEOPLE; j++) {
                if (Math.abs(positions[i] - positions[j]) == 1) {
                    simple.add(new Condition.Adjacent(i, j));
                }
                if (positions[i] < positions[j]) {
                    advanced.add(new Condition.LeftOf(i, j));
                } else {
                    advanced.add(new Condition.LeftOf(j, i));
                }
                if (positions[j] - positions[i] == 1) {
                    advanced.add(new Condition.ImmediatelyLeftOf(i, j));
                } else if (positions[i] - positions[j] == 1) {
                    advanced.add(new Condition.ImmediatelyLeftOf(j, i));
                }
                int gap = Math.abs(positions[i] - positions[j]) - 1;
                if (gap >= 1) {
                    advanced.add(new Condition.Gap(i, j, gap));
                }
            }
        }

        List<Condition> pool = new ArrayList<>();
        switch (depth) {
            case 1 -> {
                pool.addAll(simple);
                advanced.clear();
            }
            case 2 -> {
                pool.addAll(advanced.stream()
                        .filter(c -> c instanceof Condition.LeftOf)
                        .toList());
                pool.addAll(simple);
            }
            default -> {
                // 高深度优先用串联型条件，简单条件只用来补足
                pool.addAll(advanced);
                pool.addAll(simple);
            }
        }
        return dedupe(pool);
    }

    private static List<Condition> dedupe(List<Condition> conditions) {
        Set<String> seen = new LinkedHashSet<>();
        List<Condition> unique = new ArrayList<>();
        for (Condition condition : conditions) {
            String key = condition.getClass().getSimpleName() + ":" + describeKey(condition);
            if (seen.add(key)) {
                unique.add(condition);
            }
        }
        return unique;
    }

    private static String describeKey(Condition condition) {
        return switch (condition) {
            case Condition.Adjacent c -> Math.min(c.i(), c.j()) + "-" + Math.max(c.i(), c.j());
            case Condition.LeftOf c -> c.i() + "<" + c.j();
            case Condition.ImmediatelyLeftOf c -> c.i() + "|<" + c.j();
            case Condition.AtPosition c -> c.i() + "@" + c.position();
            case Condition.NotAtEnd c -> "!" + c.i();
            case Condition.Gap c -> c.i() + "~" + c.j() + "~" + c.gap();
        };
    }

    private Puzzle build(String[] people, int[] positions, List<Condition> conditions, int queryPosition) {
        List<String> order = new ArrayList<>(List.of(people));
        Collections.shuffle(order, random);

        int queriedPerson = personAt(positions, queryPosition);
        String correctPerson = people[queriedPerson];
        List<String> options = new ArrayList<>(PEOPLE);
        char letter = 'A';
        String correctOption = null;
        for (String person : order) {
            String option = letter + ". " + person;
            options.add(option);
            if (person.equals(correctPerson)) {
                correctOption = String.valueOf(letter);
            }
            letter++;
        }

        StringBuilder prompt = new StringBuilder();
        prompt.append("【逻辑推理】").append(PEOPLE)
                .append(" 个人排成一排，从左到右依次是第 1 到第 ").append(PEOPLE).append(" 个位置。\n");
        prompt.append("已知：\n");
        for (int i = 0; i < conditions.size(); i++) {
            prompt.append(i + 1).append(". ").append(conditions.get(i).describe(people)).append('\n');
        }
        prompt.append("请问：谁站在第 ").append(queryPosition + 1).append(" 个位置？\n");
        prompt.append("选项：\n");
        for (String option : options) {
            prompt.append(option).append('\n');
        }

        StringBuilder explanation = new StringBuilder();
        explanation.append("唯一排列（从左到右）：");
        for (int position = 0; position < PEOPLE; position++) {
            if (position > 0) {
                explanation.append("、");
            }
            explanation.append(people[personAt(positions, position)]);
        }
        explanation.append("；第 ").append(queryPosition + 1).append(" 个位置是 ")
                .append(correctPerson).append("，选 ").append(correctOption);

        return new Puzzle(people, positions, List.copyOf(conditions), queryPosition,
                List.copyOf(options), correctOption, prompt.toString(), explanation.toString());
    }

    private String[] pickPeople() {
        List<String> pool = new ArrayList<>(List.of(NAME_POOL));
        Collections.shuffle(pool, random);
        return pool.subList(0, PEOPLE).toArray(new String[0]);
    }

    private int[] randomPermutation() {
        List<Integer> order = new ArrayList<>(PEOPLE);
        for (int i = 0; i < PEOPLE; i++) {
            order.add(i);
        }
        Collections.shuffle(order, random);
        int[] positions = new int[PEOPLE];
        for (int person = 0; person < PEOPLE; person++) {
            positions[person] = order.get(person);
        }
        return positions;
    }

    static int personAt(int[] positions, int position) {
        for (int person = 0; person < positions.length; person++) {
            if (positions[person] == position) {
                return person;
            }
        }
        throw new IllegalArgumentException("位置越界: " + position);
    }
}
