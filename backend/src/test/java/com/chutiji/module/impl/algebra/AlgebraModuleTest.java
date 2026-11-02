package com.chutiji.module.impl.algebra;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

class AlgebraModuleTest {

    private final AlgebraModule module = new AlgebraModule();

    @Test
    @DisplayName("meta：key=algebra、type=FILL、三根轴 ENUM/INT_RANGE/BOOLEAN")
    void metaDeclaresAxes() {
        ModuleMeta meta = module.meta();
        assertEquals("algebra", meta.key());
        assertEquals("方程与代数", meta.name());
        assertEquals(QuestionType.FILL, meta.type());
        assertEquals(3, meta.axes().size());

        DifficultyAxis degree = meta.axes().get(0);
        assertEquals("degree", degree.key());
        assertEquals(AxisKind.ENUM, degree.kind());
        assertEquals("LINEAR", degree.defaultValue());
        assertEquals(List.of("LINEAR", "QUADRATIC"),
                degree.options().stream().map(o -> o.value()).toList());

        DifficultyAxis variables = meta.axes().get(1);
        assertEquals("variables", variables.key());
        assertEquals(AxisKind.INT_RANGE, variables.kind());
        assertEquals(1, variables.min());
        assertEquals(2, variables.max());
        assertEquals(1, variables.defaultValue());

        DifficultyAxis integerRoot = meta.axes().get(2);
        assertEquals("integerRoot", integerRoot.key());
        assertEquals(AxisKind.BOOLEAN, integerRoot.kind());
        assertEquals(Boolean.TRUE, integerRoot.defaultValue());
    }

    @Test
    @DisplayName("payload 只有 answerFormat/hint/unknowns，绝不含解")
    void payloadHasNoSolution() {
        GeneratedQuestion q = module.generate(new GenRequest("algebra",
                Map.of("degree", "LINEAR", "variables", 1, "integerRoot", true)));
        assertFalse(q.payload().containsKey("solutions"));
        assertFalse(q.payload().containsKey("answer"));
        assertEquals("TEXT", q.payload().get("answerFormat"));
        assertNotNull(q.payload().get("hint"));
        assertEquals(List.of("x"), q.payload().get("unknowns"));
        assertTrue(q.answer().containsKey("solutions"));
        assertTrue(q.answer().containsKey("equations"));
    }

    @Test
    @DisplayName("一元一次 20 道：把标准答案代回题面原方程成立")
    void twentyLinearRootsVerified() {
        for (int i = 0; i < 20; i++) {
            GeneratedQuestion q = module.generate(new GenRequest("algebra",
                    Map.of("degree", "LINEAR", "variables", 1, "integerRoot", true)));
            assertTrue(q.prompt().startsWith("解方程："), q.prompt());
            assertEquals(1, EquationCheckSupport.degree(q.prompt()), "应为一次方程: " + q.prompt());
            double root = rootOf(q);
            assertTrue(EquationCheckSupport.holds(q.prompt(), root),
                    "代回不成立: " + q.prompt() + " 根=" + root + " 答案=" + q.answer().get("solutions"));
            assertTrue((double) Math.rint(root) == root, "integerRoot=true 时应为整数根: " + root);
            System.out.printf("[方程] 一元一次 第 %2d 道 %s，根 %s 代回成立%n",
                    i + 1, q.prompt(), q.answer().get("solutions"));
        }
    }

    @Test
    @DisplayName("一元二次 20 道：两个根都代回原方程成立，integerRoot 时判别式为完全平方（根为有理数）")
    void twentyQuadraticRootsVerified() {
        for (int i = 0; i < 20; i++) {
            GeneratedQuestion q = module.generate(new GenRequest("algebra",
                    Map.of("degree", "QUADRATIC", "variables", 1, "integerRoot", true)));
            assertEquals(2, EquationCheckSupport.degree(q.prompt()), "应为二次方程: " + q.prompt());
            List<?> roots = (List<?>) q.answer().get("solutions");
            assertEquals(2, roots.size());
            for (Object root : roots) {
                double value = Rational.parse(String.valueOf(root)).toDouble();
                assertTrue((double) Math.rint(value) == value,
                        "integerRoot=true 时根必须是整数: " + root + " in " + q.prompt());
                assertTrue(EquationCheckSupport.holds(q.prompt(), value),
                        "代回不成立: " + q.prompt() + " 根=" + root);
            }
            System.out.printf("[方程] 一元二次 第 %2d 道 %s，根 %s 逐个代回成立%n",
                    i + 1, q.prompt(), roots);
        }
    }

    @Test
    @DisplayName("integerRoot=false 时会产出分数根，且分数根代回原方程仍成立")
    void fractionalRootsVerified() {
        int fractional = 0;
        for (int i = 0; i < 40; i++) {
            GeneratedQuestion q = module.generate(new GenRequest("algebra",
                    Map.of("degree", "LINEAR", "variables", 1, "integerRoot", false)));
            String root = String.valueOf(((List<?>) q.answer().get("solutions")).get(0));
            double value = Rational.parse(root).toDouble();
            assertTrue(EquationCheckSupport.holds(q.prompt(), value), "代回不成立: " + q.prompt() + " 根=" + root);
            if (root.contains("/")) {
                fractional++;
            }
        }
        assertTrue(fractional > 0, "integerRoot=false 时应至少出现分数根");
    }

    @Test
    @DisplayName("二元一次方程组 20 道：x、y 同时代回两条方程都成立")
    void twentySystemsVerified() {
        for (int i = 0; i < 20; i++) {
            GeneratedQuestion q = module.generate(new GenRequest("algebra",
                    Map.of("degree", "LINEAR", "variables", 2, "integerRoot", true)));
            assertTrue(q.prompt().startsWith("解方程组："), q.prompt());
            assertEquals(2, EquationCheckSupport.variableCount(q.prompt()), q.prompt());
            List<?> solutions = (List<?>) q.answer().get("solutions");
            assertEquals(2, solutions.size());
            double x = Rational.parse(String.valueOf(solutions.get(0))).toDouble();
            double y = Rational.parse(String.valueOf(solutions.get(1))).toDouble();
            assertTrue(EquationCheckSupport.holds(q.prompt(), x, y),
                    "代回不成立: " + q.prompt() + " x=" + x + " y=" + y);
            System.out.printf("[方程] 方程组 第 %2d 道 %s，x=%s y=%s 代回两条方程都成立%n",
                    i + 1, q.prompt(), x, y);
        }
    }

    @Test
    @DisplayName("判题支持 x=3、3、x=3或-2、3,-2、3 -2 等写法，多根顺序无关")
    void gradeAcceptsManyFormats() {
        Map<String, Object> linear = module.generate(new GenRequest("algebra",
                Map.of("degree", "LINEAR", "variables", 1, "integerRoot", true))).answer();
        String root = String.valueOf(((List<?>) linear.get("solutions")).get(0));
        for (String submission : List.of(root, "x=" + root, "x = " + root, " X=" + root)) {
            GradeResult result = module.grade(new GradeRequest("algebra", linear, submission));
            assertTrue(result.correct(), "应判对: " + submission + " -> " + result.explanation());
        }
        GradeResult wrong = module.grade(new GradeRequest("algebra", linear, "x=999999"));
        assertFalse(wrong.correct());
        assertEquals(root, wrong.correctAnswer());
    }

    @Test
    @DisplayName("二次方程判题：3,-2 / 3 -2 / x=3或-2 / -2,3 都判对，单个根判错")
    void gradeQuadraticAnyOrder() {
        Map<String, Object> stored = Map.of(
                "equations", List.of(List.of("1:2:0", "-1:1:0", "-6:0:0")),
                "unknowns", List.of("x"),
                "solutions", List.of("-2", "3"));
        for (String submission : List.of("3,-2", "3 -2", "x=3或-2", "-2,3", "-2或3", "3，-2", "x1=3, x2=-2")) {
            assertTrue(module.grade(new GradeRequest("algebra", stored, submission)).correct(),
                    "应判对: " + submission);
        }
        assertFalse(module.grade(new GradeRequest("algebra", stored, "3")).correct());
        assertFalse(module.grade(new GradeRequest("algebra", stored, "3,3")).correct());
        assertFalse(module.grade(new GradeRequest("algebra", stored, "3,2")).correct());
        assertFalse(module.grade(new GradeRequest("algebra", stored, "3,-2,1")).correct());
    }

    @Test
    @DisplayName("判题是代回验证而不是比字符串：等价的分数/小数写法都判对")
    void gradeIsSubstitutionNotStringMatch() {
        Map<String, Object> stored = Map.of(
                "equations", List.of(List.of("3:1:0", "-2:0:0")),
                "unknowns", List.of("x"),
                "solutions", List.of("2/3"));
        assertTrue(module.grade(new GradeRequest("algebra", stored, "2/3")).correct());
        assertTrue(module.grade(new GradeRequest("algebra", stored, "x=2/3")).correct());
        assertTrue(module.grade(new GradeRequest("algebra", stored, "4/6")).correct());
        assertTrue(module.grade(new GradeRequest("algebra", stored, "0.6666666")).correct());
        assertFalse(module.grade(new GradeRequest("algebra", stored, "0.67")).correct());
    }

    @Test
    @DisplayName("方程组判题：按 x、y 顺序写、带标签写都判对，顺序或标签写错判错")
    void gradeSystem() {
        // x + y = 8，x - y = 2  =>  x=5, y=3
        Map<String, Object> stored = Map.of(
                "equations", List.of(List.of("1:1:0", "1:0:1", "-8:0:0"), List.of("1:1:0", "-1:0:1", "-2:0:0")),
                "unknowns", List.of("x", "y"),
                "solutions", List.of("5", "3"));
        assertTrue(module.grade(new GradeRequest("algebra", stored, "5,3")).correct());
        assertTrue(module.grade(new GradeRequest("algebra", stored, "x=5,y=3")).correct());
        assertTrue(module.grade(new GradeRequest("algebra", stored, "y=3;x=5")).correct());
        assertFalse(module.grade(new GradeRequest("algebra", stored, "3,5")).correct());
        assertFalse(module.grade(new GradeRequest("algebra", stored, "x=5")).correct());
        assertFalse(module.grade(new GradeRequest("algebra", stored, "x=5,y=5")).correct());
    }

    @Test
    @DisplayName("连续出题 60 道，自判为对、错答案判错")
    void generateAndGradeConsistent() {
        String[][] configs = {
                {"LINEAR", "1", "true"},
                {"LINEAR", "1", "false"},
                {"QUADRATIC", "1", "true"},
                {"LINEAR", "2", "true"}};
        for (int i = 0; i < 60; i++) {
            String[] config = configs[i % configs.length];
            GeneratedQuestion q = module.generate(new GenRequest("algebra", Map.of(
                    "degree", config[0], "variables", Integer.parseInt(config[1]),
                    "integerRoot", Boolean.parseBoolean(config[2]))));
            String expected = String.join(",", ((List<?>) q.answer().get("solutions")).stream()
                    .map(String::valueOf).toList());
            GradeResult right = module.grade(new GradeRequest("algebra", q.answer(), expected));
            assertTrue(right.correct(), "第 " + i + " 次自判失败: " + q.prompt() + " 答案 " + expected
                    + " -> " + right.explanation());
            GradeResult bad = module.grade(new GradeRequest("algebra", q.answer(), "987654"));
            assertFalse(bad.correct(), "第 " + i + " 次错误答案被判对: " + q.prompt());
        }
    }

    private static double rootOf(GeneratedQuestion question) {
        String root = String.valueOf(((List<?>) question.answer().get("solutions")).get(0));
        Rational parsed = Rational.parse(root);
        assertNotNull(parsed, "标准答案无法解析: " + root);
        return parsed.toDouble();
    }
}
