package com.chutiji.module.impl.logic;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 测试侧的独立穷举器：只读题面文本，用全量 4! = 24 种排列朴素判定，
 * 不复用生产代码的 PermutationSolver（剪枝逻辑正是要被独立验证的部分）。
 */
final class LogicCheckSupport {

    private static final Pattern RULE_LINE = Pattern.compile("^(\\d+)\\. (.+)$");
    private static final Pattern QUERY = Pattern.compile("谁站在第 (\\d+) 个位置");
    private static final Pattern OPTION_LINE = Pattern.compile("^([A-D])\\. (.+)$", Pattern.MULTILINE);

    private LogicCheckSupport() {
    }

    /** 一条从题面解析出来的规则。 */
    record Rule(String raw, List<String> people) {
    }

    /** 题面解析结果：人名、规则、问句位置（1 起）、选项（字母 → 人名）。 */
    record Parsed(List<String> people, List<Rule> rules, int queryPosition, List<String[]> options) {
    }

    static Parsed parse(String prompt) {
        String[] lines = prompt.split("\n");
        List<String> options = new ArrayList<>();
        List<String> optionPeople = new ArrayList<>();
        Matcher optionMatcher = OPTION_LINE.matcher(prompt);
        while (optionMatcher.find()) {
            options.add(optionMatcher.group(1));
            optionPeople.add(optionMatcher.group(2).trim());
        }
        List<String[]> optionPairs = new ArrayList<>();
        for (int i = 0; i < options.size(); i++) {
            optionPairs.add(new String[] {options.get(i), optionPeople.get(i)});
        }

        List<Rule> rules = new ArrayList<>();
        boolean inside = false;
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.equals("已知：")) {
                inside = true;
                continue;
            }
            if (trimmed.startsWith("请问：")) {
                break;
            }
            if (!inside) {
                continue;
            }
            Matcher matcher = RULE_LINE.matcher(trimmed);
            if (matcher.matches()) {
                rules.add(new Rule(matcher.group(2), optionPeople));
            }
        }
        Matcher query = QUERY.matcher(prompt);
        if (!query.find()) {
            throw new AssertionError("题面里找不到问句: " + prompt);
        }
        return new Parsed(List.copyOf(optionPeople), List.copyOf(rules),
                Integer.parseInt(query.group(1)), List.copyOf(optionPairs));
    }

    /** 全量穷举：返回所有同时满足全部规则的人名排列（下标 0 = 第 1 个位置）。 */
    static List<String[]> allSolutions(Parsed parsed) {
        List<String[]> solutions = new ArrayList<>();
        permute(new ArrayList<>(parsed.people()), new ArrayList<>(), parsed.people().size(),
                parsed.rules(), solutions);
        return solutions;
    }

    private static void permute(List<String> remaining, List<String> prefix, int size,
            List<Rule> rules, List<String[]> out) {
        if (prefix.size() == size) {
            String[] arrangement = prefix.toArray(new String[0]);
            for (Rule rule : rules) {
                if (!holds(rule.raw(), rule.people(), arrangement)) {
                    return;
                }
            }
            out.add(arrangement);
            return;
        }
        for (int i = 0; i < remaining.size(); i++) {
            String picked = remaining.remove(i);
            prefix.add(picked);
            permute(remaining, prefix, size, rules, out);
            prefix.remove(prefix.size() - 1);
            remaining.add(i, picked);
        }
    }

    /** 供交叉验证：给定人员名单与规则文本，暴力列出全部满足的排列。 */
    static void permuteForTest(List<String> people, List<String> prefix, List<String> raws,
            List<String[]> out) {
        permute(new ArrayList<>(people), prefix, people.size(),
                raws.stream().map(raw -> new Rule(raw, people)).toList(), out);
        return;
    }

    /** 按题面文字判定一条规则；出现无法识别的写法直接报错，避免静默放过。 */
    static boolean holds(String raw, List<String> knownPeople, String[] arrangement) {
        List<String> named = namedPeople(raw, knownPeople);
        if (named.isEmpty()) {
            throw new AssertionError("规则里没有人名: " + raw);
        }
        if (named.size() == 1) {
            String who = named.get(0);
            int position = positionOf(arrangement, who);
            if (raw.endsWith("不在两端")) {
                return position > 0 && position < arrangement.length - 1;
            }
            Matcher at = Pattern.compile("站在第(\\d+)个位置$").matcher(raw);
            if (at.find()) {
                return position == Integer.parseInt(at.group(1)) - 1;
            }
            throw new AssertionError("测试侧无法识别的单人规则: " + raw);
        }
        if (named.size() != 2) {
            throw new AssertionError("规则里人名个数异常: " + raw);
        }
        String first = named.get(0);
        String second = named.get(1);
        int a = positionOf(arrangement, first);
        int b = positionOf(arrangement, second);
        String verb = raw.replace(first, "#").replace(second, "#");
        if (verb.equals("#和#紧挨着")) {
            return Math.abs(a - b) == 1;
        }
        if (verb.equals("#在#的左边")) {
            return a < b;
        }
        if (verb.equals("#紧挨着#的左边")) {
            return b - a == 1;
        }
        Matcher gap = Pattern.compile("^#和#之间隔着(\\d+)个人$").matcher(verb);
        if (gap.matches()) {
            return Math.abs(a - b) == Integer.parseInt(gap.group(1)) + 1;
        }
        throw new AssertionError("测试侧无法识别的双人规则: " + raw + " 动词片段=" + verb);
    }

    /** 规则文本里出现的人名，按出现位置排序。 */
    private static List<String> namedPeople(String raw, List<String> knownPeople) {
        List<int[]> found = new ArrayList<>();
        for (String person : knownPeople) {
            int index = raw.indexOf(person);
            if (index >= 0) {
                found.add(new int[] {index, knownPeople.indexOf(person)});
            }
        }
        found.sort((left, right) -> Integer.compare(left[0], right[0]));
        List<String> named = new ArrayList<>();
        for (int[] item : found) {
            named.add(knownPeople.get(item[1]));
        }
        return named;
    }

    private static int positionOf(String[] arrangement, String person) {
        for (int i = 0; i < arrangement.length; i++) {
            if (arrangement[i].equals(person)) {
                return i;
            }
        }
        throw new AssertionError("题面提到了名单外的人: " + person);
    }
}
