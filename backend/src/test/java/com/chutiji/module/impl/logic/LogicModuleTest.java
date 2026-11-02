package com.chutiji.module.impl.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.chutiji.module.api.AxisKind;
import com.chutiji.module.api.DifficultyAxis;
import com.chutiji.module.api.GenRequest;
import com.chutiji.module.api.GeneratedQuestion;
import com.chutiji.module.api.GradeRequest;
import com.chutiji.module.api.GradeResult;
import com.chutiji.module.api.ModuleMeta;
import com.chutiji.module.api.QuestionType;

class LogicModuleTest {

    private final LogicModule module = new LogicModule();

    @Test
    @DisplayName("meta：key=logic、type=CHOICE、两根轴 INT_RANGE（2-6 / 1-4）")
    void metaDeclaresAxes() {
        ModuleMeta meta = module.meta();
        assertEquals("logic", meta.key());
        assertEquals("逻辑推理", meta.name());
        assertEquals(QuestionType.CHOICE, meta.type());
        assertEquals(2, meta.axes().size());

        DifficultyAxis conditions = meta.axes().get(0);
        assertEquals("conditionCount", conditions.key());
        assertEquals(AxisKind.INT_RANGE, conditions.kind());
        assertEquals(2, conditions.min());
        assertEquals(6, conditions.max());
        assertEquals(3, conditions.defaultValue());

        DifficultyAxis depth = meta.axes().get(1);
        assertEquals("stepDepth", depth.key());
        assertEquals(AxisKind.INT_RANGE, depth.kind());
        assertEquals(1, depth.min());
        assertEquals(4, depth.max());
        assertEquals(2, depth.defaultValue());
    }

    @Test
    @DisplayName("payload 只有 options 与 answerFormat，绝不含 correctOption")
    void payloadHasNoAnswer() {
        GeneratedQuestion q = module.generate(new GenRequest("logic", Map.of(
                "conditionCount", 3, "stepDepth", 2)));
        assertFalse(q.payload().containsKey("correctOption"));
        assertFalse(q.payload().containsKey("answer"));
        assertEquals("CHOICE", q.payload().get("answerFormat"));

        @SuppressWarnings("unchecked")
        List<String> options = (List<String>) q.payload().get("options");
        assertEquals(4, options.size());
        assertEquals("A. ", options.get(0).substring(0, 3));
        assertEquals("D. ", options.get(3).substring(0, 3));
        assertTrue(q.answer().containsKey("correctOption"));
    }

    @Test
    @DisplayName("30 道：题面里每条条件都对答案排列成立，且全量穷举恰好唯一解")
    void thirtyPuzzlesAllUniqueSolutions() {
        int[][] configs = {{2, 1}, {3, 2}, {4, 2}, {5, 3}, {6, 4}};
        for (int i = 0; i < 30; i++) {
            int[] config = configs[i % configs.length];
            GeneratedQuestion q = module.generate(new GenRequest("logic", Map.of(
                    "conditionCount", config[0], "stepDepth", config[1])));

            LogicCheckSupport.Parsed parsed = LogicCheckSupport.parse(q.prompt());
            assertEquals(4, parsed.people().size(), "应有 4 个选项对应 4 个人");
            assertTrue(parsed.rules().size() >= 2, "条件数至少 2 条: " + q.prompt());

            List<String[]> solutions = LogicCheckSupport.allSolutions(parsed);
            assertEquals(1, solutions.size(),
                    "第 " + (i + 1) + " 题不是唯一解（解数=" + solutions.size() + "）:\n" + q.prompt());

            // 唯一解必须与 answer 里声明的选项一致
            String[] unique = solutions.get(0);
            String correctPerson = unique[parsed.queryPosition() - 1];
            String expectedLetter = null;
            for (String[] option : parsed.options()) {
                if (option[1].equals(correctPerson)) {
                    expectedLetter = option[0];
                }
            }
            assertNotNull(expectedLetter, "选项里找不到答案对应的人: " + correctPerson);
            assertEquals(expectedLetter, q.answer().get("correctOption"),
                    "第 " + (i + 1) + " 题 answer 与穷举结果不一致:\n" + q.prompt());

            System.out.printf("[逻辑推理] 第 %2d 题 条件=%d 步深=%d 解数=%d 唯一解排列=%s 答案=%s%n",
                    i + 1, parsed.rules().size(), config[1], solutions.size(),
                    String.join(" > ", unique), q.answer().get("correctOption"));
        }
        System.out.println("[逻辑推理] 30 道全部穷举验证：解数均为 1");
    }

    @Test
    @DisplayName("条件数轴严格生效：请求 3~6 条时题面条数正好等于请求值；请求 2 条时补到唯一解所需的 3 条")
    void conditionCountControlsPrompt() {
        for (int requested = 3; requested <= 6; requested++) {
            for (int round = 0; round < 5; round++) {
                GeneratedQuestion many = module.generate(new GenRequest("logic", Map.of(
                        "conditionCount", requested, "stepDepth", 2)));
                int actual = LogicCheckSupport.parse(many.prompt()).rules().size();
                assertEquals(requested, actual,
                        "请求 " + requested + " 条条件，实际给了 " + actual + " 条:\n" + many.prompt());
            }
        }

        GeneratedQuestion few = module.generate(new GenRequest("logic", Map.of(
                "conditionCount", 2, "stepDepth", 1)));
        assertTrue(LogicCheckSupport.parse(few.prompt()).rules().size() >= 2);
        assertEquals(1, LogicCheckSupport.allSolutions(LogicCheckSupport.parse(few.prompt())).size());
    }

    @Test
    @DisplayName("题面写全四个选项的完整文字，不只给字母")
    void promptCarriesFullOptionText() {
        GeneratedQuestion q = module.generate(new GenRequest("logic", Map.of(
                "conditionCount", 3, "stepDepth", 2)));
        LogicCheckSupport.Parsed parsed = LogicCheckSupport.parse(q.prompt());
        assertEquals(4, parsed.options().size());
        for (String[] option : parsed.options()) {
            assertFalse(option[1].isBlank(), "选项文字不能为空");
            assertTrue(q.prompt().contains(option[0] + ". " + option[1]));
        }
        assertTrue(q.prompt().contains("谁站在第"));
        assertTrue(q.prompt().contains("选项："));
    }

    @Test
    @DisplayName("判题：A/B/C/D 判对，大小写与带文字写法都接受，错选项判错并回传正确答案")
    void gradesOptionLetter() {
        Map<String, Object> stored = Map.of("correctOption", "B");
        for (String submission : List.of("B", "b", " B ", "B.", "B. 李四", "选B")) {
            GradeResult result = module.grade(new GradeRequest("logic", stored, submission));
            assertTrue(result.correct(), "应判对: " + submission);
        }
        for (String submission : List.of("A", "C", "D")) {
            GradeResult result = module.grade(new GradeRequest("logic", stored, submission));
            assertFalse(result.correct(), "应判错: " + submission);
            assertEquals("B", result.correctAnswer());
        }
        GradeResult invalid = module.grade(new GradeRequest("logic", stored, "不知道"));
        assertFalse(invalid.correct());
        assertTrue(invalid.explanation().contains("请选择"));
    }

    @Test
    @DisplayName("连续出题 30 道，自判为对、错答案判错")
    void generateAndGradeConsistent() {
        for (int i = 0; i < 30; i++) {
            GeneratedQuestion q = module.generate(new GenRequest("logic", Map.of(
                    "conditionCount", 2 + i % 5, "stepDepth", 1 + i % 4)));
            String expected = String.valueOf(q.answer().get("correctOption"));
            GradeResult right = module.grade(new GradeRequest("logic", q.answer(), expected));
            assertTrue(right.correct(), "第 " + i + " 次自判失败: " + q.prompt());
            String wrong = expected.equals("A") ? "B" : "A";
            GradeResult bad = module.grade(new GradeRequest("logic", q.answer(), wrong));
            assertFalse(bad.correct(), "第 " + i + " 次错误答案被判对");
        }
    }

    @Test
    @DisplayName("题面不会直接告诉被问的人站在第几个位置")
    void queryAnswerIsNotGivenDirectly() {
        for (int i = 0; i < 15; i++) {
            GeneratedQuestion q = module.generate(new GenRequest("logic", Map.of(
                    "conditionCount", 3 + i % 4, "stepDepth", 1 + i % 4)));
            LogicCheckSupport.Parsed parsed = LogicCheckSupport.parse(q.prompt());
            List<String[]> solutions = LogicCheckSupport.allSolutions(parsed);
            String answerPerson = solutions.get(0)[parsed.queryPosition() - 1];
            for (LogicCheckSupport.Rule rule : parsed.rules()) {
                assertFalse(rule.raw().startsWith(answerPerson + "站在第"),
                        "条件直接泄露了答案位置: " + rule.raw() + "\n" + q.prompt());
            }
        }
    }

    @Test
    @DisplayName("干扰项互不相同，且正确答案字母唯一")
    void optionsAreDistinct() {
        for (int i = 0; i < 10; i++) {
            GeneratedQuestion q = module.generate(new GenRequest("logic", Map.of(
                    "conditionCount", 3, "stepDepth", 2)));
            @SuppressWarnings("unchecked")
            List<String> options = (List<String>) q.payload().get("options");
            List<String> people = new ArrayList<>();
            for (String option : options) {
                people.add(option.substring(3));
            }
            assertEquals(4, people.stream().distinct().count(), "选项文字重复: " + options);
            String correctOption = String.valueOf(q.answer().get("correctOption"));
            LogicCheckSupport.Parsed parsed = LogicCheckSupport.parse(q.prompt());
            List<String[]> solutions = LogicCheckSupport.allSolutions(parsed);
            assertEquals(1, solutions.size());
            String correctPerson = solutions.get(0)[parsed.queryPosition() - 1];
            String optionPerson = null;
            for (String[] option : parsed.options()) {
                if (option[0].equals(correctOption)) {
                    optionPerson = option[1];
                }
            }
            assertEquals(correctPerson, optionPerson, "正确答案字母指向的人不对");
            assertEquals(1, q.payload().get("options").toString()
                    .split(correctPerson, -1).length - 1, "正确答案的人只应出现一次");
        }
    }

    @Test
    @DisplayName("穷举求解器与全量暴力一致：随机条件集的解数与独立实现对得上")
    void solverMatchesBruteForce() {
        java.util.Random random = new java.util.Random(20261102L);
        String[] people = {"甲", "乙", "丙", "丁"};
        for (int round = 0; round < 60; round++) {
            int[] target = randomPermutation(random);
            List<Condition> conditions = new ArrayList<>();
            List<String> raws = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                for (int j = i + 1; j < 4; j++) {
                    if (random.nextBoolean()) {
                        if (target[i] < target[j]) {
                            conditions.add(new Condition.LeftOf(i, j));
                            raws.add(people[i] + "在" + people[j] + "的左边");
                        } else {
                            conditions.add(new Condition.LeftOf(j, i));
                            raws.add(people[j] + "在" + people[i] + "的左边");
                        }
                    }
                    if (random.nextBoolean()) {
                        conditions.add(new Condition.Adjacent(i, j));
                        raws.add(people[i] + "和" + people[j] + "紧挨着");
                    }
                }
            }
            for (int i = 0; i < 4; i++) {
                if (target[i] != 0 && target[i] != 3 && random.nextBoolean()) {
                    conditions.add(new Condition.NotAtEnd(i));
                    raws.add(people[i] + "不在两端");
                }
            }
            int viaSolver = PermutationSolver.countUpTo(4, conditions, 25);

            List<String[]> all = new ArrayList<>();
            LogicCheckSupport.permuteForTest(List.of(people), new ArrayList<>(), raws, all);
            assertEquals(all.size(), viaSolver, "第 " + round + " 组条件解数不一致: " + raws);
        }
    }

    private static int[] randomPermutation(java.util.Random random) {
        List<Integer> order = new ArrayList<>(List.of(0, 1, 2, 3));
        java.util.Collections.shuffle(order, random);
        int[] positions = new int[4];
        for (int person = 0; person < 4; person++) {
            positions[person] = order.get(person);
        }
        return positions;
    }
}
