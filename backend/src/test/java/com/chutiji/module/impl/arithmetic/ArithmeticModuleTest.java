package com.chutiji.module.impl.arithmetic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.chutiji.module.api.AxisKind;
import com.chutiji.module.api.GenRequest;
import com.chutiji.module.api.GeneratedQuestion;
import com.chutiji.module.api.GradeRequest;
import com.chutiji.module.api.GradeResult;
import com.chutiji.module.api.ModuleMeta;
import com.chutiji.module.api.QuestionType;

class ArithmeticModuleTest {

    private final ArithmeticModule module = new ArithmeticModule();

    @Test
    @DisplayName("meta 声明四根难度轴，类型为 FILL")
    void metaDeclaresAxes() {
        ModuleMeta meta = module.meta();
        assertEquals("arithmetic", meta.key());
        assertEquals(QuestionType.FILL, meta.type());
        assertEquals(4, meta.axes().size());
        assertEquals(AxisKind.INT_RANGE, meta.axes().get(0).kind());
        assertEquals("maxOperand", meta.axes().get(0).key());
        assertEquals(AxisKind.BOOLEAN, meta.axes().get(2).kind());
    }

    @Test
    @DisplayName("生成的题目 payload 里没有 answer")
    void payloadHasNoAnswer() {
        GeneratedQuestion q = module.generate(new GenRequest("arithmetic", Map.of(
                "maxOperand", 100, "operatorCount", 2, "allowDivision", true)));
        assertNotNull(q.prompt());
        assertTrue(q.prompt().startsWith("计算："));
        assertFalse(q.payload().containsKey("answer"));
        assertFalse(q.payload().containsKey("correctAnswer"));
        assertTrue(q.answer().containsKey("value"));
    }

    @Test
    @DisplayName("判题：正确答案判对，错误答案判错并返回正确答案")
    void gradesCorrectly() {
        Map<String, Object> stored = Map.of("value", "1/3");
        GradeResult right = module.grade(new GradeRequest("arithmetic", stored, "1/3"));
        assertTrue(right.correct());
        assertEquals("1/3", right.correctAnswer());

        GradeResult equivalent = module.grade(new GradeRequest("arithmetic", stored, "2/6"));
        assertTrue(equivalent.correct());

        GradeResult wrong = module.grade(new GradeRequest("arithmetic", stored, "0.5"));
        assertFalse(wrong.correct());
        assertEquals("1/3", wrong.correctAnswer());

        GradeResult integer = module.grade(new GradeRequest("arithmetic", Map.of("value", "31"), "31"));
        assertTrue(integer.correct());
        GradeResult integerWrong = module.grade(new GradeRequest("arithmetic", Map.of("value", "31"), "32"));
        assertFalse(integerWrong.correct());
    }

    @Test
    @DisplayName("连续出题 100 次，题面答案与判题结果一致")
    void generateAndGradeConsistent() {
        List<Map<String, Object>> configs = List.of(
                Map.of("maxOperand", 100, "operatorCount", 2, "allowDivision", true),
                Map.of("maxOperand", 100, "operatorCount", 3, "allowParentheses", true),
                Map.of("maxOperand", 50, "operatorCount", 1, "allowDivision", false));
        for (int i = 0; i < 100; i++) {
            GeneratedQuestion q = module.generate(
                    new GenRequest("arithmetic", configs.get(i % configs.size())));
            String expected = q.answer().get("value").toString();
            GradeResult result = module.grade(new GradeRequest("arithmetic", q.answer(), expected));
            assertTrue(result.correct(), "第 " + i + " 次自判失败: " + q.prompt() + " 答案 " + expected);
            GradeResult bad = module.grade(new GradeRequest("arithmetic", q.answer(), "999999"));
            assertFalse(bad.correct(), "第 " + i + " 次错误答案被判对: " + q.prompt());
        }
    }
}
