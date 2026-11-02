package com.chutiji.module.impl.geometry;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;

/**
 * 参数化几何题生成器。
 *
 * <p>五类题型 × 两个复杂度档，共十条生成路径。每条路径的做法固定为三步：
 * 先选参数 → 再用手算公式算答案 → 最后用同一份参数画 SVG。参数是唯一的真相，
 * 图形和答案都是它的函数，所以「图画的是 3 cm、答案按 4 cm 算」这类错位在结构上不可能发生。</p>
 */
final class GeometryGenerator {

    /** 教材惯例：圆一律取 π = 3.14，否则答案落不到两位小数。 */
    static final double PI = 3.14;

    /** 勾股数表：保证斜边是整数，答案整洁。 */
    private static final int[][] PYTHAGOREAN = {
            {3, 4, 5}, {6, 8, 10}, {5, 12, 13}, {8, 15, 17}, {9, 12, 15},
            {7, 24, 25}, {12, 16, 20}, {9, 40, 41}, {20, 21, 29}};

    private static final double SIDE_FONT = 13.0;

    private final Random random;

    GeometryGenerator(Random random) {
        this.random = random;
    }

    GeometryProblem next(ShapeKind kind, boolean composite, boolean needAuxiliary) {
        return switch (kind) {
            case TRIANGLE -> composite ? triangleArea(needAuxiliary) : trianglePerimeter(needAuxiliary);
            case RECTANGLE -> composite ? rectangleDiagonal(needAuxiliary) : rectanglePerimeter(needAuxiliary);
            case CIRCLE -> composite ? circleArea(needAuxiliary) : circlePerimeter(needAuxiliary);
            case PYTHAGORAS -> composite ? pythagorasLeg(needAuxiliary) : pythagorasHypotenuse(needAuxiliary);
            case SIMILAR_TRIANGLE -> composite ? similarPerimeter(needAuxiliary) : similarSide(needAuxiliary);
        };
    }

    // ---------- 三角形 ----------

    /** BASIC：三边已知求周长。 */
    private GeometryProblem trianglePerimeter(boolean auxiliary) {
        double a;
        double b;
        double c;
        int guard = 0;
        do {
            a = 3 + random.nextInt(7);
            b = 3 + random.nextInt(7);
            c = 3 + random.nextInt(7);
            guard++;
        } while (!(a + b > c && a + c > b && b + c > a) && guard < 200);
        if (!(a + b > c && a + c > b && b + c > a)) {
            a = 3;
            b = 4;
            c = 5;
        }

        String labelA = format(a) + " " + UNIT;
        String labelB = format(b) + " " + UNIT;
        String labelC = format(c) + " " + UNIT;

        SvgCanvas canvas = new SvgCanvas();
        double scale = 190.0 / Math.max(a, Math.max(b, c));
        drawTriangle(canvas, a, b, c, scale, 0, 0, labelA, labelB, labelC);
        if (auxiliary) {
            double[] p = placeTriangle(a, b, c, scale, 0, 0);
            canvas.line(p[4], p[5], p[4], p[1], true);
        }

        Map<String, Double> given = new LinkedHashMap<>();
        given.put("a", a);
        given.put("b", b);
        given.put("c", c);
        double value = a + b + c;
        String prompt = "三角形的三条边分别是 " + format(a) + " " + UNIT + "、" + format(b) + " " + UNIT
                + "、" + format(c) + " " + UNIT + "，求它的周长。";
        String explanation = "周长 = " + format(a) + " + " + format(b) + " + " + format(c)
                + " = " + format(value) + " " + UNIT;
        return new GeometryProblem(Formula.TRIANGLE_PERIMETER, prompt, round2(value), given, numberHint(),
                canvas.build(), explanation);
    }

    /** COMPOSITE：直角三角形两条直角边求面积。 */
    private GeometryProblem triangleArea(boolean auxiliary) {
        double a = 3 + random.nextInt(8);
        double b = 3 + random.nextInt(8);
        if (Math.round(a * b) % 2 != 0) {
            b = b + 1;
        }

        String labelA = format(a) + " " + UNIT;
        String labelB = format(b) + " " + UNIT;

        SvgCanvas canvas = new SvgCanvas();
        double scale = 180.0 / Math.max(a, b);
        drawRightTriangle(canvas, a, b, scale, labelA, labelB, null, auxiliary);

        Map<String, Double> given = new LinkedHashMap<>();
        given.put("a", a);
        given.put("b", b);
        double value = a * b / 2.0;
        String prompt = "一个直角三角形的两条直角边分别是 " + format(a) + " " + UNIT + " 和 "
                + format(b) + " " + UNIT + "，求它的面积。";
        String explanation = "面积 = " + format(a) + " × " + format(b) + " ÷ 2 = " + format(value) + " " + UNIT;
        return new GeometryProblem(Formula.TRIANGLE_AREA, prompt, round2(value), given, numberHint(),
                canvas.build(), explanation);
    }

    // ---------- 矩形 ----------

    /** BASIC：长宽已知求周长。 */
    private GeometryProblem rectanglePerimeter(boolean auxiliary) {
        double a = 5 + random.nextInt(8);
        double b = 3 + random.nextInt(6);
        if (a == b) {
            b = b + 2;
        }

        SvgCanvas canvas = new SvgCanvas();
        drawRectangle(canvas, a, b, false, auxiliary);

        Map<String, Double> given = new LinkedHashMap<>();
        given.put("a", a);
        given.put("b", b);
        double value = 2 * (a + b);
        String prompt = "一个矩形的长是 " + format(a) + " " + UNIT + "，宽是 " + format(b) + " " + UNIT
                + "，求它的周长。";
        String explanation = "周长 = (" + format(a) + " + " + format(b) + ") × 2 = " + format(value) + " " + UNIT;
        return new GeometryProblem(Formula.RECTANGLE_PERIMETER, prompt, round2(value), given, numberHint(),
                canvas.build(), explanation);
    }

    /** COMPOSITE：长宽已知求对角线，是勾股定理的第二步用法。 */
    private GeometryProblem rectangleDiagonal(boolean auxiliary) {
        int[] triple = PYTHAGOREAN[random.nextInt(PYTHAGOREAN.length)];
        double a = triple[0];
        double b = triple[1];
        double value = triple[2];

        SvgCanvas canvas = new SvgCanvas();
        drawRectangle(canvas, a, b, true, auxiliary);

        Map<String, Double> given = new LinkedHashMap<>();
        given.put("a", a);
        given.put("b", b);
        String prompt = "一个矩形的长是 " + format(a) + " " + UNIT + "，宽是 " + format(b) + " " + UNIT
                + "，求它的对角线长度（图中虚线）。";
        String explanation = "对角线 = √(" + format(a) + "² + " + format(b) + "²) = √"
                + format(a * a + b * b) + " = " + format(value) + " " + UNIT;
        return new GeometryProblem(Formula.RECTANGLE_DIAGONAL, prompt, round2(value), given, numberHint(),
                canvas.build(), explanation);
    }

    // ---------- 圆 ----------

    /** BASIC：半径求周长。 */
    private GeometryProblem circlePerimeter(boolean auxiliary) {
        double r = 2 + random.nextInt(9);
        SvgCanvas canvas = new SvgCanvas();
        drawCircle(canvas, false, auxiliary, format(r) + " " + UNIT);

        Map<String, Double> given = new LinkedHashMap<>();
        given.put("r", r);
        double value = 2 * PI * r;
        String prompt = "一个圆的半径是 " + format(r) + " " + UNIT + "，求它的周长（π 取 3.14）。";
        String explanation = "周长 = 2 × 3.14 × " + format(r) + " = " + format(value) + " " + UNIT;
        return new GeometryProblem(Formula.CIRCLE_CIRCUMFERENCE, prompt, round2(value), given, piHint(),
                canvas.build(), explanation);
    }

    /** COMPOSITE：直径求面积，先半径再平方，两步。 */
    private GeometryProblem circleArea(boolean auxiliary) {
        double d = 2 * (2 + random.nextInt(9));
        double r = d / 2;
        SvgCanvas canvas = new SvgCanvas();
        drawCircle(canvas, true, auxiliary, format(d) + " " + UNIT);

        Map<String, Double> given = new LinkedHashMap<>();
        given.put("d", d);
        double value = PI * r * r;
        String prompt = "一个圆的直径是 " + format(d) + " " + UNIT + "，求它的面积（π 取 3.14）。";
        String explanation = "半径 = " + format(d) + " ÷ 2 = " + format(r) + "；面积 = 3.14 × "
                + format(r) + "² = " + format(value) + " " + UNIT;
        return new GeometryProblem(Formula.CIRCLE_AREA, prompt, round2(value), given, piHint(),
                canvas.build(), explanation);
    }

    // ---------- 勾股定理 ----------

    /** BASIC：两条直角边求斜边。 */
    private GeometryProblem pythagorasHypotenuse(boolean auxiliary) {
        int[] triple = PYTHAGOREAN[random.nextInt(PYTHAGOREAN.length)];
        double a = triple[0];
        double b = triple[1];
        double value = triple[2];

        SvgCanvas canvas = new SvgCanvas();
        double scale = 180.0 / Math.max(a, b);
        drawRightTriangle(canvas, a, b, scale, format(a) + " " + UNIT, format(b) + " " + UNIT,
                "?", auxiliary);

        Map<String, Double> given = new LinkedHashMap<>();
        given.put("a", a);
        given.put("b", b);
        String prompt = "一个直角三角形的两条直角边分别是 " + format(a) + " " + UNIT + " 和 "
                + format(b) + " " + UNIT + "，求斜边的长度。";
        String explanation = "斜边 = √(" + format(a) + "² + " + format(b) + "²) = √"
                + format(a * a + b * b) + " = " + format(value) + " " + UNIT;
        return new GeometryProblem(Formula.PYTHAGORAS_HYPOTENUSE, prompt, round2(value), given, numberHint(),
                canvas.build(), explanation);
    }

    /** COMPOSITE：已知斜边与一条直角边求另一条，需要先变形再开方。 */
    private GeometryProblem pythagorasLeg(boolean auxiliary) {
        int[] triple = PYTHAGOREAN[random.nextInt(PYTHAGOREAN.length)];
        double a = triple[0];
        double b = triple[1];
        double c = triple[2];

        SvgCanvas canvas = new SvgCanvas();
        double scale = 180.0 / Math.max(a, b);
        drawRightTriangle(canvas, a, b, scale, format(a) + " " + UNIT, "?", format(c) + " " + UNIT, auxiliary);

        Map<String, Double> given = new LinkedHashMap<>();
        given.put("a", a);
        given.put("c", c);
        String prompt = "一个直角三角形的斜边是 " + format(c) + " " + UNIT + "，一条直角边是 "
                + format(a) + " " + UNIT + "，求另一条直角边的长度。";
        String explanation = "另一条直角边 = √(" + format(c) + "² - " + format(a) + "²) = √"
                + format(c * c - a * a) + " = " + format(b) + " " + UNIT;
        return new GeometryProblem(Formula.PYTHAGORAS_LEG, prompt, round2(b), given, numberHint(),
                canvas.build(), explanation);
    }

    // ---------- 相似三角形 ----------

    /** BASIC：两个三角形相似，已知一对应边与另一条边，求对应边。 */
    private GeometryProblem similarSide(boolean auxiliary) {
        double[] small = smallTriangle();
        double a = small[0];
        double b = small[1];
        double c = small[2];
        double k = 2 + random.nextInt(3);
        double bigA = k * a;
        double value = k * b;

        SvgCanvas canvas = new SvgCanvas();
        drawSimilarPair(canvas, a, b, c, k,
                new String[]{format(a) + " " + UNIT, format(b) + " " + UNIT, format(c) + " " + UNIT},
                new String[]{format(bigA) + " " + UNIT, "?", null}, auxiliary);

        Map<String, Double> given = new LinkedHashMap<>();
        given.put("a", a);
        given.put("b", b);
        given.put("A", bigA);
        String prompt = "两个三角形相似。小三角形的边 " + format(a) + " " + UNIT + " 对应大三角形的 "
                + format(bigA) + " " + UNIT + "，小三角形的另一条边是 " + format(b) + " " + UNIT
                + "，求大三角形中与它对应的边。";
        String explanation = "相似比 = " + format(bigA) + " ÷ " + format(a) + " = " + format(k)
                + "；对应边 = " + format(b) + " × " + format(k) + " = " + format(value) + " " + UNIT;
        return new GeometryProblem(Formula.SIMILAR_SIDE, prompt, round2(value), given, numberHint(),
                canvas.build(), explanation);
    }

    /** COMPOSITE：由一对应边求出相似比，再求大三角形周长（周长比等于相似比）。 */
    private GeometryProblem similarPerimeter(boolean auxiliary) {
        double[] small = smallTriangle();
        double a = small[0];
        double b = small[1];
        double c = small[2];
        double k = 2 + random.nextInt(2);
        double bigA = k * a;
        double value = k * (a + b + c);

        SvgCanvas canvas = new SvgCanvas();
        drawSimilarPair(canvas, a, b, c, k,
                new String[]{format(a) + " " + UNIT, format(b) + " " + UNIT, format(c) + " " + UNIT},
                new String[]{format(bigA) + " " + UNIT, null, null}, auxiliary);

        Map<String, Double> given = new LinkedHashMap<>();
        given.put("a", a);
        given.put("b", b);
        given.put("c", c);
        given.put("A", bigA);
        String prompt = "两个三角形相似。小三角形的三条边分别是 " + format(a) + " " + UNIT + "、"
                + format(b) + " " + UNIT + "、" + format(c) + " " + UNIT + "；大三角形中与 "
                + format(a) + " " + UNIT + " 对应的边是 " + format(bigA) + " " + UNIT
                + "，求大三角形的周长。";
        String explanation = "相似比 = " + format(bigA) + " ÷ " + format(a) + " = " + format(k)
                + "；小三角形周长 = " + format(a + b + c) + "；大三角形周长 = "
                + format(a + b + c) + " × " + format(k) + " = " + format(value) + " " + UNIT;
        return new GeometryProblem(Formula.SIMILAR_PERIMETER, prompt, round2(value), given, numberHint(),
                canvas.build(), explanation);
    }

    // ---------- 画图 ----------

    /** 小三角形的三边，满足三角不等式，且最大边有界便于排版。 */
    private double[] smallTriangle() {
        int guard = 0;
        while (guard++ < 200) {
            double a = 3 + random.nextInt(6);
            double b = 3 + random.nextInt(6);
            double c = 3 + random.nextInt(6);
            if (a + b > c && a + c > b && b + c > a) {
                return new double[]{a, b, c};
            }
        }
        return new double[]{3, 4, 5};
    }

    /**
     * 按三边定一个三角形：底边落在 (ox,oy)-(ox+base*scale,oy)，第三个顶点在上方。
     * 展开成数组 [p0x,p0y,p1x,p1y,apexx,apexy]。
     */
    private static double[] placeTriangle(double a, double b, double c, double scale, double ox, double oy) {
        double x = (c * c + a * a - b * b) / (2 * c);
        double y = Math.sqrt(Math.max(0.0, a * a - x * x));
        return new double[]{ox, oy, ox + c * scale, oy, ox + x * scale, oy - y * scale};
    }

    private static void drawTriangle(SvgCanvas canvas, double a, double b, double c, double scale,
                                     double ox, double oy, String labelA, String labelB, String labelC) {
        double[] p = placeTriangle(a, b, c, scale, ox, oy);
        canvas.polygon(new double[]{p[0], p[2], p[4]}, new double[]{p[1], p[3], p[5]}, false);
        double cx = (p[0] + p[2] + p[4]) / 3.0;
        double cy = (p[1] + p[3] + p[5]) / 3.0;
        sideLabel(canvas, p[0], p[1], p[4], p[5], cx, cy, labelA);
        sideLabel(canvas, p[2], p[3], p[4], p[5], cx, cy, labelB);
        sideLabel(canvas, p[0], p[1], p[2], p[3], cx, cy, labelC);
    }

    /** 三边标签统一贴在边中点、朝三角形外侧推一点，避免压在图形上。 */
    private static void sideLabel(SvgCanvas canvas, double x1, double y1, double x2, double y2,
                                  double cx, double cy, String text) {
        if (text == null || text.isEmpty()) {
            return;
        }
        double mx = (x1 + x2) / 2.0;
        double my = (y1 + y2) / 2.0;
        double dx = mx - cx;
        double dy = my - cy;
        double len = Math.hypot(dx, dy);
        if (len < 1e-9) {
            dx = 0;
            dy = 1;
            len = 1;
        }
        canvas.label(mx + dx / len * 16, my + dy / len * 16 + SIDE_FONT * 0.35, text, "middle");
    }

    /** 直角三角形：直角顶点在原点，一条直角边向右、一条向上，斜边连起来。 */
    private static void drawRightTriangle(SvgCanvas canvas, double legX, double legY, double scale,
                                          String labelX, String labelY, String labelHyp, boolean auxiliary) {
        double w = legX * scale;
        double h = legY * scale;
        canvas.polygon(new double[]{0, w, 0}, new double[]{0, 0, -h}, false);
        canvas.rightAngle(0, 0, 1, 0, 0, -1);
        if (labelX != null) {
            canvas.label(w / 2, 20, labelX, "middle");
        }
        if (labelY != null) {
            canvas.label(-10, -h / 2 + 5, labelY, "end");
        }
        if (labelHyp != null) {
            canvas.label(w / 2 + 16, -h / 2 + 16, labelHyp, "middle");
        }
        if (auxiliary) {
            // 直角顶点到斜边的垂线：经典的辅助线画法
            double t = (w * w) / (w * w + h * h);
            canvas.line(0, 0, w - t * w, -t * h, true);
        }
    }

    /**
     * 矩形。{@code diagonal} 只在求对角线的题里为真——求周长时绝不能在图上多画一条线，
     * 否则学生看到的是题目里没给的已知条件。对角线是待求量，画虚线并标问号。
     */
    private static void drawRectangle(SvgCanvas canvas, double a, double b, boolean diagonal, boolean auxiliary) {
        double scale = 190.0 / Math.max(a, b);
        double w = a * scale;
        double h = b * scale;
        canvas.rect(0, 0, w, h, false);
        canvas.label(w / 2, h + 20, format(a) + " " + UNIT, "middle");
        canvas.label(-10, h / 2 + 5, format(b) + " " + UNIT, "end");
        canvas.rightAngle(0, h, 1, 0, 0, -1);
        if (diagonal) {
            canvas.line(0, 0, w, h, true);
            canvas.label(w / 2 + 14, h / 2 - 8, "?", "middle");
        }
        if (auxiliary) {
            canvas.line(w, 0, 0, h, true);
        }
    }

    /**
     * 圆：半径或直径二者之一画实线并标注数字，另一条（如果开了辅助线）画虚线。
     */
    private static void drawCircle(SvgCanvas canvas, boolean diameterGiven, boolean auxiliary, String text) {
        double r = 70;
        canvas.circle(0, 0, r, false);
        canvas.dot(0, 0);
        // 半径线略微倾斜，避免和直径重合
        double angle = Math.toRadians(-35);
        double ex = r * Math.cos(angle);
        double ey = r * Math.sin(angle);
        if (diameterGiven) {
            canvas.line(-r, 0, r, 0, false);
            canvas.label(0, -8, text, "middle");
            if (auxiliary) {
                canvas.line(0, 0, ex, ey, true);
            }
        } else {
            canvas.line(0, 0, ex, ey, false);
            canvas.label(ex / 2 + 6, ey / 2 - 6, text, "middle");
            if (auxiliary) {
                canvas.line(-r, 0, r, 0, true);
            }
        }
    }

    /** 并排画两个相似三角形：小三角形在左，大三角形按相似比 k 放大在右。 */
    private static void drawSimilarPair(SvgCanvas canvas, double a, double b, double c, double k,
                                        String[] smallLabels, String[] bigLabels, boolean auxiliary) {
        double gap = 0.55 * c;
        double natural = c + gap + k * c;
        double scale = 300.0 / natural;
        double bigOriginX = (c + gap) * scale;
        double[] small = placeTriangle(a, b, c, scale, 0, 0);
        double[] big = placeTriangle(a * k, b * k, c * k, scale * k, bigOriginX, 0);

        drawTriangle(canvas, a, b, c, scale, 0, 0, smallLabels[0], smallLabels[1], smallLabels[2]);
        drawTriangle(canvas, a * k, b * k, c * k, scale * k, bigOriginX, 0,
                bigLabels[0], bigLabels[1], bigLabels[2]);

        if (auxiliary) {
            // 辅助线：把两组对应顶点连起来，相似关系一眼可见
            for (int i = 0; i < 3; i++) {
                canvas.line(small[i * 2], small[i * 2 + 1], big[i * 2], big[i * 2 + 1], true);
            }
        }
    }

    // ---------- 数值 ----------

    private static final String UNIT = "cm";

    static String numberHint() {
        return "只填数值，保留两位小数";
    }

    static String piHint() {
        return "π 取 3.14，只填数值，保留两位小数";
    }

    static String format(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP)
                .stripTrailingZeros().toPlainString();
    }

    static double round2(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }
}
