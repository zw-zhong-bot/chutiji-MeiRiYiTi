package com.chutiji.module.impl.geometry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SvgCanvasTest {

    @Test
    @DisplayName("XML 转义：& < > \" ' 全部转义，标签注入无路可走")
    void escapesDangerousCharacters() {
        assertEquals("&lt;script&gt;", SvgCanvas.esc("<script>"));
        assertEquals("a&amp;b", SvgCanvas.esc("a&b"));
        assertEquals("&quot;x&quot;", SvgCanvas.esc("\"x\""));
        assertEquals("&#39;y&#39;", SvgCanvas.esc("'y'"));
        assertEquals("", SvgCanvas.esc(null));
        assertEquals("3 cm", SvgCanvas.esc("3 cm"));
    }

    @Test
    @DisplayName("转义后的恶意文本进不了标签：注入的 <script> 在 SVG 里是文本，不是元素")
    void injectionStaysText() {
        SvgCanvas canvas = new SvgCanvas();
        canvas.label(10, 20, "</text><script>alert(1)</script>", "start");
        String svg = canvas.build();
        assertFalse(svg.contains("<script>"), "注入的 script 标签不应出现在 SVG 里: " + svg);
        assertTrue(svg.contains("&lt;script&gt;"), "应当以转义文本形式出现: " + svg);
    }

    @Test
    @DisplayName("坐标格式化：整数不带小数尾巴，两位小数保留，负数正常")
    void formatsNumbers() {
        assertEquals("10", SvgCanvas.n(10.0));
        assertEquals("0", SvgCanvas.n(0.0));
        assertEquals("10.5", SvgCanvas.n(10.5));
        assertEquals("-3.25", SvgCanvas.n(-3.25));
        assertEquals("3.14", SvgCanvas.n(Math.PI));
    }

    @Test
    @DisplayName("viewBox 由包围盒反推：画在哪儿都包得住，不会溢出")
    void viewBoxWrapsContent() {
        SvgCanvas canvas = new SvgCanvas();
        canvas.line(100, 100, 200, 150, false);
        canvas.label(150, 160, "5 cm", "middle");
        String svg = canvas.build();

        assertTrue(svg.startsWith("<svg "), "必须以 <svg 开头: " + svg);
        assertTrue(svg.endsWith("</svg>"), "必须以 </svg> 结尾: " + svg);
        assertTrue(svg.contains("viewBox='"), "必须带 viewBox: " + svg);

        String viewBox = svg.substring(svg.indexOf("viewBox='") + 9);
        viewBox = viewBox.substring(0, viewBox.indexOf('\''));
        String[] parts = viewBox.split(" ");
        assertEquals(4, parts.length, "viewBox 应为四个数: " + viewBox);

        double x = Double.parseDouble(parts[0]);
        double y = Double.parseDouble(parts[1]);
        double w = Double.parseDouble(parts[2]);
        double h = Double.parseDouble(parts[3]);

        // 每个画过的点都必须落在 viewBox 内
        for (double px : new double[]{100, 200}) {
            assertTrue(px >= x && px <= x + w, "点 " + px + " 越出 viewBox 横向范围 " + viewBox);
        }
        for (double py : new double[]{100, 150}) {
            assertTrue(py >= y && py <= y + h, "点 " + py + " 越出 viewBox 纵向范围 " + viewBox);
        }
        assertTrue(w > 0 && h > 0, "viewBox 尺寸必须为正: " + viewBox);
    }

    @Test
    @DisplayName("空画布也能出一个合法 SVG，不会抛")
    void emptyCanvasStillBuilds() {
        String svg = new SvgCanvas().build();
        assertTrue(svg.contains("<svg"));
        assertTrue(svg.contains("</svg>"));
        assertTrue(svg.contains("viewBox"));
    }
}
