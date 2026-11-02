package com.chutiji.module.impl.unit;

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

class UnitModuleTest {

    private final UnitModule module = new UnitModule();

    @Test
    @DisplayName("meta：key=unit、type=FILL、两根轴 ENUM + BOOLEAN，五族选项齐全")
    void metaDeclaresAxes() {
        ModuleMeta meta = module.meta();
        assertEquals("unit", meta.key());
        assertEquals("单位换算", meta.name());
        assertEquals(QuestionType.FILL, meta.type());
        assertEquals(2, meta.axes().size());

        DifficultyAxis family = meta.axes().get(0);
        assertEquals("unitFamily", family.key());
        assertEquals(AxisKind.ENUM, family.kind());
        assertEquals("LENGTH", family.defaultValue());
        assertNotNull(family.options());
        List<String> values = family.options().stream().map(o -> o.value()).toList();
        assertEquals(List.of("LENGTH", "WEIGHT", "VOLUME", "TIME", "AREA"), values);

        DifficultyAxis cross = meta.axes().get(1);
        assertEquals("crossMagnitude", cross.key());
        assertEquals(AxisKind.BOOLEAN, cross.kind());
        assertEquals(Boolean.FALSE, cross.defaultValue());
    }

    @Test
    @DisplayName("五族各生成 10 道：按单位表独立验算数值正确，payload 不下发答案")
    void fiveFamiliesTenEachVerified() {
        for (UnitFamily family : UnitFamily.values()) {
            for (int i = 0; i < 10; i++) {
                boolean cross = i % 2 == 1;
                GeneratedQuestion q = module.generate(new GenRequest("unit",
                        Map.of("unitFamily", family.name(), "crossMagnitude", cross)));
                assertFalse(q.payload().containsKey("answer"));
                assertFalse(q.payload().containsKey("value"));
                assertEquals("NUMBER", q.payload().get("answerFormat"));
                String targetUnit = (String) q.payload().get("unit");

                double expected = (double) q.answer().get("value");

                // 独立验算：从题面解析源数值与源单位，用单位表重新算一遍
                double recomputed = UnitCheckSupport.recompute(q.prompt(), family, targetUnit);
                double relative = Math.abs(recomputed - expected) / Math.max(1e-12, Math.abs(expected));
                assertTrue(relative <= 1e-9,
                        family + " 第 " + (i + 1) + " 题验算不符: 题面 " + q.prompt()
                                + " 期望 " + expected + " 重算 " + recomputed);
                System.out.printf("[单位换算] %-8s 第 %2d 题（跨量级=%s）%s  ->  期望 %s，独立重算 %s%n",
                        family, i + 1, cross, q.prompt(), UnitModule.formatNumber(expected),
                        UnitModule.formatNumber(recomputed));
            }
        }
        System.out.println("[单位换算] 五族各 10 道共 50 道全部独立验算通过");
    }

    @Test
    @DisplayName("crossMagnitude=true 时每道题的换算比达到 10^3，false 时低于 10^3")
    void crossMagnitudeControlsRatio() {
        for (UnitFamily family : UnitFamily.values()) {
            for (int i = 0; i < 5; i++) {
                GeneratedQuestion big = module.generate(new GenRequest("unit",
                        Map.of("unitFamily", family.name(), "crossMagnitude", true)));
                assertTrue(UnitCheckSupport.ratio(big.prompt(), family) >= 1000.0,
                        "跨量级题比值不足 10^3: " + big.prompt());

                GeneratedQuestion small = module.generate(new GenRequest("unit",
                        Map.of("unitFamily", family.name(), "crossMagnitude", false)));
                assertTrue(UnitCheckSupport.ratio(small.prompt(), family) < 1000.0,
                        "非跨量级题比值过大: " + small.prompt());
            }
        }
    }

    @Test
    @DisplayName("题面同时写出源单位与目标单位，payload.unit 就是目标单位")
    void promptNamesBothUnits() {
        GeneratedQuestion q = module.generate(new GenRequest("unit",
                Map.of("unitFamily", "WEIGHT", "crossMagnitude", false)));
        assertTrue(q.prompt().startsWith("单位换算："));
        assertTrue(q.prompt().contains(" = ? "), "题面应给出目标单位: " + q.prompt());
        assertTrue(q.prompt().endsWith((String) q.payload().get("unit")));
        assertTrue(q.explanation().contains("="), "解析里应给出完整换算等式");
    }

    @Test
    @DisplayName("答案不带 double 尾数垃圾：300 平方毫米→平方厘米 必须是 3，不是 2.9999999999999996")
    void answersAreCleanDecimals() {
        for (UnitFamily family : UnitFamily.values()) {
            for (int i = 0; i < 20; i++) {
                GeneratedQuestion q = module.generate(new GenRequest("unit",
                        Map.of("unitFamily", family.name(), "crossMagnitude", i % 2 == 1)));
                String text = UnitModule.formatNumber((double) q.answer().get("value"));
                assertFalse(text.matches("-?\\d+\\.\\d{10,}"),
                        family + " 答案出现了 double 尾数垃圾: " + text + " （" + q.prompt() + "）");
                assertTrue(text.length() <= 16, "答案文本过长: " + text + " （" + q.prompt() + "）");
            }
        }
    }

    @Test
    @DisplayName("判题：正确值判对，容差 1e-3 内判对，超差判错并给出标准答案")
    void gradesWithTolerance() {
        Map<String, Object> stored = Map.of("value", 1250.0);

        assertTrue(module.grade(new GradeRequest("unit", stored, "1250")).correct());
        assertTrue(module.grade(new GradeRequest("unit", stored, "1250.0")).correct());
        assertTrue(module.grade(new GradeRequest("unit", stored, " 1250 米 ")).correct());
        assertTrue(module.grade(new GradeRequest("unit", stored, "1,250")).correct());
        // 相对误差 1e-3 => 1250 的容差是 1.25
        assertTrue(module.grade(new GradeRequest("unit", stored, "1251.2")).correct());
        assertFalse(module.grade(new GradeRequest("unit", stored, "1251.3")).correct());
        assertFalse(module.grade(new GradeRequest("unit", stored, "125")).correct());

        GradeResult wrong = module.grade(new GradeRequest("unit", stored, "999"));
        assertFalse(wrong.correct());
        assertEquals("1250", wrong.correctAnswer());

        GradeResult garbage = module.grade(new GradeRequest("unit", stored, "不知道"));
        assertFalse(garbage.correct());
        assertTrue(garbage.explanation().contains("格式"));
    }

    @Test
    @DisplayName("连续出题 100 次，自判为对、错答案判错")
    void generateAndGradeConsistent() {
        UnitFamily[] families = UnitFamily.values();
        for (int i = 0; i < 100; i++) {
            UnitFamily family = families[i % families.length];
            GeneratedQuestion q = module.generate(new GenRequest("unit",
                    Map.of("unitFamily", family.name(), "crossMagnitude", i % 2 == 0)));
            String expected = q.answer().get("value").toString();
            GradeResult right = module.grade(new GradeRequest("unit", q.answer(), expected));
            assertTrue(right.correct(), "第 " + i + " 次自判失败: " + q.prompt());
            GradeResult bad = module.grade(new GradeRequest("unit", q.answer(), "-123456"));
            assertFalse(bad.correct(), "第 " + i + " 次错误答案被判对: " + q.prompt());
        }
    }
}
