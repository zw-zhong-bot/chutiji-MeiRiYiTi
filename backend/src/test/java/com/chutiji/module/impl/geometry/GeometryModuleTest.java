package com.chutiji.module.impl.geometry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
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

class GeometryModuleTest {

    /** 五类题型 × 两个复杂度 = 十条生成路径，每条都要被覆盖到。 */
    private static final int PER_PATH = 10;

    private final GeometryModule module = new GeometryModule();

    @Test
    @DisplayName("meta：key=geometry、type=FIGURE、两根轴 ENUM + BOOLEAN")
    void metaDeclaresAxes() {
        ModuleMeta meta = module.meta();
        assertEquals("geometry", meta.key());
        assertEquals("几何图形", meta.name());
        assertEquals(QuestionType.FIGURE, meta.type());
        assertEquals(2, meta.axes().size());

        DifficultyAxis complexity = meta.axes().get(0);
        assertEquals("shapeComplexity", complexity.key());
        assertEquals(AxisKind.ENUM, complexity.kind());
        assertEquals("BASIC", complexity.defaultValue());
        assertNotNull(complexity.options());
        assertEquals(List.of("BASIC", "COMPOSITE"),
                complexity.options().stream().map(o -> o.value()).toList());

        DifficultyAxis auxiliary = meta.axes().get(1);
        assertEquals("needAuxiliary", auxiliary.key());
        assertEquals(AxisKind.BOOLEAN, auxiliary.kind());
        assertEquals(Boolean.FALSE, auxiliary.defaultValue());
        assertFalse(meta.poolBased());
        assertEquals(180, meta.timeLimitSec());
    }

    @Test
    @DisplayName("payload 结构：svg + answerFormat + unit + hint，且绝不含答案")
    void payloadShapeAndNoLeak() {
        for (int i = 0; i < 60; i++) {
            GeneratedQuestion q = generate(i % 2 == 0, i % 3 == 0);
            Map<String, Object> payload = q.payload();

            assertFalse(payload.containsKey("answer"), "payload 不得含 answer");
            assertFalse(payload.containsKey("value"), "payload 不得含 value");
            assertFalse(payload.containsKey("tolerance"), "payload 不得含 tolerance");
            assertEquals("NUMBER", payload.get("answerFormat"));
            assertEquals("cm", payload.get("unit"));
            assertNotNull(payload.get("hint"));

            String svg = (String) payload.get("svg");
            assertNotNull(svg, "SVG 必须随 payload 下发");
            assertFalse(svg.isBlank(), "SVG 不能是空串");
            assertTrue(svg.contains("<svg"), "SVG 必须含 <svg");
            assertTrue(svg.contains("</svg>"), "SVG 必须含 </svg>");
            assertTrue(svg.contains("viewBox="), "SVG 必须带 viewBox");
        }
    }

    /**
     * 验收判据 2 第一条：五类题型各生成 10 道，验算答案数值正确。
     *
     * <p>按 formula 分流而不是按随机抽中的 shapeKind 过滤，保证每条生成路径确实跑够 10 道，
     * 不会出现「随机没抽到因此没测到」的假绿。</p>
     */
    @Test
    @DisplayName("五类题型各 10 道：按公式独立验算答案数值")
    void fiveShapesTenEachVerified() {
        Map<Formula, Integer> counted = new LinkedHashMap<>();
        for (Formula formula : Formula.values()) {
            counted.put(formula, 0);
        }

        int guard = 0;
        while (counted.values().stream().anyMatch(v -> v < PER_PATH) && guard++ < 20000) {
            GeneratedQuestion q = generate(guard % 2 == 0, guard % 3 == 0);
            Formula formula = Formula.valueOf((String) q.answer().get("formula"));
            if (counted.get(formula) >= PER_PATH) {
                continue;
            }
            int index = counted.get(formula) + 1;

            double expected = expectedValue(formula, q.answer());
            double actual = ((Number) q.answer().get("value")).doubleValue();
            assertEquals(expected, actual, 1e-9,
                    formula + " 第 " + index + " 道答案不符：" + q.prompt());
            counted.put(formula, index);
            System.out.printf("[几何] %-22s 第 %2d 道 %s -> 答案 %s（独立验算 %s）%n",
                    formula, index, q.prompt(), GeometryModule.format(actual), GeometryModule.format(expected));
        }

        for (Map.Entry<Formula, Integer> entry : counted.entrySet()) {
            assertEquals(PER_PATH, entry.getValue(), entry.getKey() + " 覆盖不足");
        }
        System.out.println("[几何] 十条生成路径各 " + PER_PATH + " 道，共 " + (PER_PATH * Formula.values().length)
                + " 道全部独立验算通过");
    }

    /** 独立验算：只读题目标注的 formula 与已知量，按公式重算，不看生成器内部。 */
    private static double expectedValue(Formula formula, Map<String, Object> answer) {
        return switch (formula) {
            case TRIANGLE_PERIMETER ->
                    num(answer, "given_a") + num(answer, "given_b") + num(answer, "given_c");
            case TRIANGLE_AREA -> num(answer, "given_a") * num(answer, "given_b") / 2.0;
            case RECTANGLE_PERIMETER -> 2 * (num(answer, "given_a") + num(answer, "given_b"));
            case RECTANGLE_DIAGONAL -> Math.hypot(num(answer, "given_a"), num(answer, "given_b"));
            case CIRCLE_CIRCUMFERENCE -> 2 * GeometryGenerator.PI * num(answer, "given_r");
            case CIRCLE_AREA -> {
                double r = num(answer, "given_d") / 2;
                yield GeometryGenerator.PI * r * r;
            }
            case PYTHAGORAS_HYPOTENUSE ->
                    Math.hypot(num(answer, "given_a"), num(answer, "given_b"));
            case PYTHAGORAS_LEG -> {
                double c = num(answer, "given_c");
                double a = num(answer, "given_a");
                yield Math.sqrt(c * c - a * a);
            }
            case SIMILAR_SIDE -> num(answer, "given_A") / num(answer, "given_a") * num(answer, "given_b");
            case SIMILAR_PERIMETER ->
                    num(answer, "given_A") / num(answer, "given_a")
                            * (num(answer, "given_a") + num(answer, "given_b") + num(answer, "given_c"));
        };
    }

    private static double num(Map<String, Object> answer, String key) {
        Object raw = answer.get(key);
        assertNotNull(raw, "答案缺少已知量 " + key);
        return ((Number) raw).doubleValue();
    }

    /**
     * 验收判据 2 第四条：图形-答案一致性。
     * 从 SVG 字符串里解析出的标注数值，必须能和已知量/答案对上关系——不是比对生成器内部变量。
     */
    @Test
    @DisplayName("图形-答案一致性：SVG 标注数值能与答案算出关系（抽样）")
    void svgMatchesAnswer() {
        int checked = 0;
        for (int i = 0; i < 200 && checked < 80; i++) {
            GeneratedQuestion q = generate(i % 2 == 0, i % 3 == 0);
            String svg = (String) q.payload().get("svg");
            Map<String, Object> answer = q.answer();
            Formula formula = Formula.valueOf((String) answer.get("formula"));
            double value = ((Number) answer.get("value")).doubleValue();

            List<Double> drawn = GeometryCheckSupport.numbers(svg);
            assertFalse(drawn.isEmpty(), formula + " 的 SVG 里没有任何数值标注：" + q.prompt());

            switch (formula) {
                case TRIANGLE_PERIMETER -> {
                    double a = num(answer, "given_a");
                    double b = num(answer, "given_b");
                    double c = num(answer, "given_c");
                    assertDrawn(svg, a, b, c);
                    assertEquals(GeometryGenerator.round2(a + b + c), value, 1e-9,
                            "图上三边之和必须等于答案");
                }
                case TRIANGLE_AREA -> {
                    double a = num(answer, "given_a");
                    double b = num(answer, "given_b");
                    assertDrawn(svg, a, b);
                    assertEquals(GeometryGenerator.round2(a * b / 2.0), value, 1e-9,
                            "图上两直角边算出的面积必须等于答案");
                }
                case RECTANGLE_PERIMETER -> {
                    double a = num(answer, "given_a");
                    double b = num(answer, "given_b");
                    assertDrawn(svg, a, b);
                    assertEquals(GeometryGenerator.round2(2 * (a + b)), value, 1e-9);
                }
                case RECTANGLE_DIAGONAL -> {
                    double a = num(answer, "given_a");
                    double b = num(answer, "given_b");
                    assertDrawn(svg, a, b);
                    assertEquals(GeometryGenerator.round2(Math.hypot(a, b)), value, 1e-9);
                }
                case CIRCLE_CIRCUMFERENCE -> {
                    double r = num(answer, "given_r");
                    assertDrawn(svg, r);
                    assertEquals(GeometryGenerator.round2(2 * GeometryGenerator.PI * r), value, 1e-9);
                }
                case CIRCLE_AREA -> {
                    double d = num(answer, "given_d");
                    assertDrawn(svg, d);
                    assertEquals(GeometryGenerator.round2(GeometryGenerator.PI * (d / 2) * (d / 2)), value, 1e-9);
                }
                case PYTHAGORAS_HYPOTENUSE -> {
                    double a = num(answer, "given_a");
                    double b = num(answer, "given_b");
                    assertDrawn(svg, a, b);
                    assertEquals(GeometryGenerator.round2(Math.hypot(a, b)), value, 1e-9);
                }
                case PYTHAGORAS_LEG -> {
                    double a = num(answer, "given_a");
                    double c = num(answer, "given_c");
                    assertDrawn(svg, a, c);
                    assertEquals(GeometryGenerator.round2(Math.sqrt(c * c - a * a)), value, 1e-9);
                }
                case SIMILAR_SIDE -> {
                    double a = num(answer, "given_a");
                    double b = num(answer, "given_b");
                    double bigA = num(answer, "given_A");
                    assertDrawn(svg, a, b, bigA);
                    assertEquals(GeometryGenerator.round2(bigA / a * b), value, 1e-9);
                }
                case SIMILAR_PERIMETER -> {
                    double a = num(answer, "given_a");
                    double b = num(answer, "given_b");
                    double c = num(answer, "given_c");
                    double bigA = num(answer, "given_A");
                    assertDrawn(svg, a, b, c, bigA);
                    assertEquals(GeometryGenerator.round2(bigA / a * (a + b + c)), value, 1e-9);
                }
                default -> throw new IllegalStateException("未覆盖的公式 " + formula);
            }
            checked++;
        }
        assertEquals(80, checked, "抽样数量不足");
        System.out.println("[几何] 图形-答案一致性抽样通过 " + checked + " 道");
    }

    private static void assertDrawn(String svg, double... expected) {
        for (double e : expected) {
            assertTrue(GeometryCheckSupport.hasNumber(svg, e),
                    "数值 " + e + " 没有出现在 SVG 标注里：" + GeometryCheckSupport.describe(svg));
        }
    }

    @Test
    @DisplayName("判题：24.5 / 24.50 / 24.500 判对，24.6 判错，容差 1e-2")
    void gradesNumericFormats() {
        Map<String, Object> stored = Map.of("value", 24.5, "tolerance", 1e-2);

        assertTrue(module.grade(new GradeRequest("geometry", stored, "24.5")).correct());
        assertTrue(module.grade(new GradeRequest("geometry", stored, "24.50")).correct());
        assertTrue(module.grade(new GradeRequest("geometry", stored, "24.500")).correct());
        assertTrue(module.grade(new GradeRequest("geometry", stored, " 24.5 cm ")).correct());
        assertTrue(module.grade(new GradeRequest("geometry", stored, "24.51")).correct());
        assertTrue(module.grade(new GradeRequest("geometry", stored, "24.49")).correct());
        assertFalse(module.grade(new GradeRequest("geometry", stored, "24.6")).correct());
        assertFalse(module.grade(new GradeRequest("geometry", stored, "24.4")).correct());

        GradeResult wrong = module.grade(new GradeRequest("geometry", stored, "24.6"));
        assertEquals("24.5", wrong.correctAnswer());
        assertTrue(wrong.explanation().contains("24.5"));

        GradeResult garbage = module.grade(new GradeRequest("geometry", stored, "不知道"));
        assertFalse(garbage.correct());
        assertTrue(garbage.explanation().contains("格式"));
    }

    @Test
    @DisplayName("出题 200 道后自判：答案字符串判对，错误答案判错")
    void generateAndGradeConsistent() {
        for (int i = 0; i < 200; i++) {
            GeneratedQuestion q = generate(i % 2 == 0, i % 5 == 0);
            double value = ((Number) q.answer().get("value")).doubleValue();
            GradeResult right = module.grade(new GradeRequest("geometry", q.answer(),
                    GeometryModule.format(value)));
            assertTrue(right.correct(), "第 " + i + " 次自判失败: " + q.prompt() + " 答案 " + value);

            GradeResult bad = module.grade(new GradeRequest("geometry", q.answer(), "-98765.4321"));
            assertFalse(bad.correct(), "第 " + i + " 次错误答案被判对: " + q.prompt());
        }
    }

    @Test
    @DisplayName("图形不溢出：viewBox 覆盖所有图形元素坐标，宽度高度为正")
    void svgNeverOverflows() {
        for (int i = 0; i < 60; i++) {
            String svg = (String) generate(i % 2 == 0, i % 3 == 0).payload().get("svg");
            String viewBox = svg.substring(svg.indexOf("viewBox='") + 9);
            viewBox = viewBox.substring(0, viewBox.indexOf('\''));
            String[] parts = viewBox.split(" ");
            assertEquals(4, parts.length, "viewBox 格式: " + viewBox);
            double x = Double.parseDouble(parts[0]);
            double y = Double.parseDouble(parts[1]);
            double w = Double.parseDouble(parts[2]);
            double h = Double.parseDouble(parts[3]);
            assertTrue(w > 0 && h > 0, "viewBox 尺寸必须为正: " + viewBox);
            assertTrue(w < 2000 && h < 2000, "viewBox 尺寸异常大: " + viewBox);

            // 只校验图形元素（circle / line / polygon / path / rect）的坐标，
            // 跳过 <text>：文字是给图做标注的，位置与字号、锚点相关，不属于"图形溢出"的语义。
            // 早期版本用 (?:x1|cx|x) 全量匹配，会把 <text x=...> 也算进去，遂偶发误报。
            String graphicsOnly = stripTextElements(svg);

            java.util.regex.Matcher mx = java.util.regex.Pattern
                    .compile("(?:x1|cx|x)='(-?\\d+(?:\\.\\d+)?)'").matcher(graphicsOnly);
            while (mx.find()) {
                double px = Double.parseDouble(mx.group(1));
                assertTrue(px >= x - 0.5 && px <= x + w + 0.5, "横坐标 " + px + " 溢出 viewBox " + viewBox);
            }
            java.util.regex.Matcher my = java.util.regex.Pattern
                    .compile("(?:y1|cy|y)='(-?\\d+(?:\\.\\d+)?)'").matcher(graphicsOnly);
            while (my.find()) {
                double py = Double.parseDouble(my.group(1));
                assertTrue(py >= y - 0.5 && py <= y + h + 0.5, "纵坐标 " + py + " 溢出 viewBox " + viewBox);
            }
        }
    }

    /** 去掉所有 &lt;text&gt;...&lt;/text&gt; 片段，只留图形元素便于坐标校验。 */
    private static String stripTextElements(String svg) {
        return svg.replaceAll("(?s)<text\\b[^>]*>.*?</text>", "");
    }

    @Test
    @DisplayName("needAuxiliary 开关决定虚线：开启时每道题都有，关闭时只有「求对角线」这种本身就该虚线画的情况例外")
    void auxiliaryDrawsDashedLines() {
        for (int i = 0; i < 60; i++) {
            String complexity = i % 2 == 0 ? "BASIC" : "COMPOSITE";

            GeneratedQuestion on = module.generate(new GenRequest("geometry",
                    Map.of("shapeComplexity", complexity, "needAuxiliary", true)));
            assertTrue(((String) on.payload().get("svg")).contains("stroke-dasharray"),
                    "开启辅助线后每道题都应有虚线: " + on.prompt());

            GeneratedQuestion off = module.generate(new GenRequest("geometry",
                    Map.of("shapeComplexity", complexity, "needAuxiliary", false)));
            Formula formula = Formula.valueOf((String) off.answer().get("formula"));
            boolean dashed = ((String) off.payload().get("svg")).contains("stroke-dasharray");
            if (formula == Formula.RECTANGLE_DIAGONAL) {
                // 对角线是待求未知量，无论开关如何都用虚线画，与题面「图中虚线」一致
                assertTrue(dashed, "对角线题必须用虚线画待求对角线: " + off.prompt());
            } else {
                assertFalse(dashed, "未开辅助线时不该出现虚线: " + formula + " / " + off.prompt());
            }
        }
    }

    @Test
    @DisplayName("求周长的题不许在对角线上多画线：图上只能有题目给出的已知量")
    void perimeterProblemsDoNotLeakExtraConstruction() {
        int checked = 0;
        for (int i = 0; i < 400 && checked < 30; i++) {
            GeneratedQuestion q = generate(i % 2 == 0, false);
            Formula formula = Formula.valueOf((String) q.answer().get("formula"));
            String svg = (String) q.payload().get("svg");

            if (formula == Formula.RECTANGLE_PERIMETER) {
                // 矩形用 <rect> 画，没开辅助线时不该出现任何 <line>
                assertFalse(svg.contains("<line"),
                        "求周长的矩形题不该画对角线/辅助线: " + q.prompt() + " -> " + svg);
                checked++;
            }
            if (formula == Formula.RECTANGLE_DIAGONAL) {
                // 对角线是待求量，必须画成虚线并标问号
                assertTrue(svg.contains("stroke-dasharray"), "对角线题必须画出虚线对角线: " + svg);
                assertTrue(GeometryCheckSupport.texts(svg).contains("?"), "对角线应标问号: " + svg);
                checked++;
            }
        }
        assertEquals(30, checked, "两种矩形题各需抽样 15 道，实际 " + checked);
    }

    @Test
    @DisplayName("hint 不重复写单位：单位由前端单独渲染，payload.hint 只说格式")
    void hintDoesNotRepeatUnit() {
        for (int i = 0; i < 40; i++) {
            String hint = (String) generate(i % 2 == 0, i % 3 == 0).payload().get("hint");
            assertFalse(hint.contains("单位 cm"),
                    "hint 里不该再写单位，前端已显示（" + hint + "）");
        }
    }

    @Test
    @DisplayName("SVG 抗注入：标注文字里的尖括号被转义，不会变成新标签")
    void svgEscapesLabels() {
        SvgCanvas canvas = new SvgCanvas();
        canvas.label(0, 0, "</text><script>alert('x')</script>", "start");
        String svg = canvas.build();
        assertFalse(svg.contains("<script"), "注入的 script 必须被转义: " + svg);
        assertTrue(svg.contains("&lt;script&gt;"), "应以转义文本出现: " + svg);
    }

    private GeneratedQuestion generate(boolean composite, boolean auxiliary) {
        return module.generate(new GenRequest("geometry",
                Map.of("shapeComplexity", composite ? "COMPOSITE" : "BASIC", "needAuxiliary", auxiliary)));
    }
}
