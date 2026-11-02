package com.chutiji.module.impl.geometry;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 验收辅助：把下发出去的 SVG 字符串里的标注文字抽出来还原成数值，
 * 供「图形与答案一致」的交叉验算使用。
 *
 * <p>它只读 SVG 字符串本身，不认识 {@link GeometryGenerator} 的任何内部变量，
 * 所以验的是真的画在图上、发给前端的东西。</p>
 */
final class GeometryCheckSupport {

    private static final Pattern TEXT = Pattern.compile("<text[^>]*>(.*?)</text>", Pattern.DOTALL);
    private static final Pattern NUMBER = Pattern.compile("[-+]?\\d+(?:\\.\\d+)?");

    private GeometryCheckSupport() {
    }

    /** SVG 里所有 &lt;text&gt; 的文本内容（已反转义）。 */
    static List<String> texts(String svg) {
        List<String> out = new ArrayList<>();
        Matcher matcher = TEXT.matcher(svg == null ? "" : svg);
        while (matcher.find()) {
            out.add(unescape(matcher.group(1)));
        }
        return out;
    }

    /** SVG 标注里出现的全部数值，按出现顺序。 */
    static List<Double> numbers(String svg) {
        List<Double> out = new ArrayList<>();
        for (String text : texts(svg)) {
            Matcher matcher = NUMBER.matcher(text);
            while (matcher.find()) {
                out.add(Double.parseDouble(matcher.group()));
            }
        }
        return out;
    }

    /** 某个数值是否作为标注出现在图上（容忍 6 位小数以内的格式差异）。 */
    static boolean hasNumber(String svg, double expected) {
        for (double found : numbers(svg)) {
            if (Math.abs(found - expected) < 1e-6) {
                return true;
            }
        }
        return false;
    }

    static String describe(String svg) {
        return "texts=" + texts(svg) + " numbers=" + numbers(svg);
    }

    private static String unescape(String raw) {
        return raw.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"")
                .replace("&#39;", "'").replace("&amp;", "&");
    }
}
