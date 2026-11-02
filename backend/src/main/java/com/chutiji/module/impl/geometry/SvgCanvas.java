package com.chutiji.module.impl.geometry;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 极简 SVG 画布：纯字符串拼接，不引第三方 SVG 库。
 *
 * <p>两条硬要求：其一，所有进入标签的文字都过 {@link #esc(String)}——SVG 会被前端塞进
 * v-html，即使是自产内容也要挡住标签注入；其二，viewBox 由"实际画过的元素包围盒 + 内边距"
 * 反推出来，所以图形永远不会溢出画布，也没有任何手调的固定尺寸。</p>
 */
final class SvgCanvas {

    /** 图形四周留白。 */
    private static final double PAD = 16.0;

    private static final double FONT = 13.0;
    private static final String INK = "#1f2430";
    private static final String SOFT = "#6b7280";

    private final StringBuilder body = new StringBuilder();
    private double minX = Double.POSITIVE_INFINITY;
    private double minY = Double.POSITIVE_INFINITY;
    private double maxX = Double.NEGATIVE_INFINITY;
    private double maxY = Double.NEGATIVE_INFINITY;

    /** XML 转义：& < > 与控制字符转义，双引号单引号一并处理，文本与属性都安全。 */
    static String esc(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "";
        }
        StringBuilder out = new StringBuilder(raw.length() + 8);
        for (int i = 0; i < raw.length(); i++) {
            char ch = raw.charAt(i);
            switch (ch) {
                case '&' -> out.append("&amp;");
                case '<' -> out.append("&lt;");
                case '>' -> out.append("&gt;");
                case '"' -> out.append("&quot;");
                case '\'' -> out.append("&#39;");
                default -> out.append(ch);
            }
        }
        return out.toString();
    }

    /** 坐标格式：最多两位小数，去掉无意义的尾零（10.00 → 10）。 */
    static String n(double value) {
        if (value == 0.0) {
            return "0";
        }
        BigDecimal decimal = BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros();
        return decimal.toPlainString();
    }

    void line(double x1, double y1, double x2, double y2, boolean dashed) {
        track(x1, y1);
        track(x2, y2);
        body.append("<line x1='").append(n(x1)).append("' y1='").append(n(y1))
                .append("' x2='").append(n(x2)).append("' y2='").append(n(y2))
                .append("' stroke='").append(INK).append("' stroke-width='").append(dashed ? "1.4" : "2")
                .append("'").append(dashed ? " stroke-dasharray='6 5'" : "").append(" />");
    }

    void polygon(double[] xs, double[] ys, boolean dashed) {
        StringBuilder points = new StringBuilder();
        for (int i = 0; i < xs.length; i++) {
            track(xs[i], ys[i]);
            if (i > 0) {
                points.append(' ');
            }
            points.append(n(xs[i])).append(',').append(n(ys[i]));
        }
        body.append("<polygon points='").append(points).append("' fill='none' stroke='").append(INK)
                .append("' stroke-width='").append(dashed ? "1.4" : "2").append("'")
                .append(dashed ? " stroke-dasharray='6 5'" : "").append(" />");
    }

    void rect(double x, double y, double width, double height, boolean dashed) {
        track(x, y);
        track(x + width, y + height);
        body.append("<rect x='").append(n(x)).append("' y='").append(n(y))
                .append("' width='").append(n(width)).append("' height='").append(n(height))
                .append("' fill='none' stroke='").append(dashed ? SOFT : INK)
                .append("' stroke-width='").append(dashed ? "1.4" : "2").append("'")
                .append(dashed ? " stroke-dasharray='6 5'" : "").append(" />");
    }

    void circle(double cx, double cy, double r, boolean dashed) {
        track(cx - r, cy - r);
        track(cx + r, cy + r);
        body.append("<circle cx='").append(n(cx)).append("' cy='").append(n(cy))
                .append("' r='").append(n(r)).append("' fill='none' stroke='")
                .append(dashed ? SOFT : INK).append("' stroke-width='").append(dashed ? "1.4" : "2")
                .append("'").append(dashed ? " stroke-dasharray='6 5'" : "").append(" />");
    }

    /** 圆心 / 直角顶点这类需要点出来的小圆点。 */
    void dot(double cx, double cy) {
        track(cx - 2.5, cy - 2.5);
        track(cx + 2.5, cy + 2.5);
        body.append("<circle cx='").append(n(cx)).append("' cy='").append(n(cy))
                .append("' r='2.5' fill='").append(INK).append("' />");
    }

    /**
     * 直角符号：在 (x,y) 处，沿 u、v 两个单位方向画一个小方框。
     */
    void rightAngle(double x, double y, double ux, double uy, double vx, double vy) {
        double s = 9.0;
        double ax = x + ux * s;
        double ay = y + uy * s;
        double bx = ax + vx * s;
        double by = ay + vy * s;
        double cx = x + vx * s;
        double cy = y + vy * s;
        track(x, y);
        track(bx, by);
        body.append("<path d='M ").append(n(ax)).append(' ').append(n(ay))
                .append(" L ").append(n(bx)).append(' ').append(n(by))
                .append(" L ").append(n(cx)).append(' ').append(n(cy))
                .append("' fill='none' stroke='").append(INK).append("' stroke-width='1.6' />");
    }

    void label(double x, double y, String content, String anchor) {
        label(x, y, content, anchor, FONT);
    }

    void label(double x, double y, String content, String anchor, double size) {
        String text = esc(content);
        double width = estimate(content, size);
        double left = switch (anchor) {
            case "middle" -> x - width / 2;
            case "end" -> x - width;
            default -> x;
        };
        track(left, y - size);
        track(left + width, y + size * 0.4);
        body.append("<text x='").append(n(x)).append("' y='").append(n(y))
                .append("' font-family='system-ui, sans-serif' font-size='").append(n(size))
                .append("' fill='").append(INK).append("' text-anchor='").append(esc(anchor)).append("'>")
                .append(text).append("</text>");
    }

    /** 只累积包围盒，不落任何元素。 */
    void reserve(double x1, double y1, double x2, double y2) {
        track(x1, y1);
        track(x2, y2);
    }

    /** 收尾：按包围盒算 viewBox，宽高同步，前端直接 v-html 就能显示。 */
    String build() {
        if (body.length() == 0) {
            track(0, 0);
            track(100, 100);
        }
        String viewX = n(minX - PAD);
        String viewY = n(minY - PAD);
        String width = n(Math.ceil(maxX - minX + PAD * 2));
        String height = n(Math.ceil(maxY - minY + PAD * 2));
        return "<svg xmlns='http://www.w3.org/2000/svg' viewBox='" + viewX + " " + viewY + " " + width
                + " " + height + "' width='" + width + "' height='" + height
                + "' style='max-width:100%;height:auto' role='img' aria-label='几何图形'>"
                + body
                + "</svg>";
    }

    private void track(double x, double y) {
        minX = Math.min(minX, x);
        minY = Math.min(minY, y);
        maxX = Math.max(maxX, x);
        maxY = Math.max(maxY, y);
    }

    private static double estimate(String content, double size) {
        if (content == null) {
            return 0;
        }
        double width = 0;
        for (int i = 0; i < content.length(); i++) {
            width += content.charAt(i) > 0x2e80 ? size : size * 0.56;
        }
        return width;
    }
}
