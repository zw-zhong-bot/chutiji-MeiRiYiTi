package com.chutiji.module.impl.algebra;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 解析用户提交的答案。支持 x=3、3、x=3或-2、3,-2、3 -2、x1=3, x2=-2，
 * 以及分数 2/3、小数 0.5；无法解析返回 null。
 */
final class SolutionParser {

    /** label 可能为空；只认紧贴等号的单个字母，兼容 x1、x₂ 这类写法里的下标数字。 */
    private static final Pattern ITEM = Pattern.compile(
            "(?:([a-zA-Z])[0-9]?\\s*=\\s*)?(-?\\d+(?:\\.\\d+)?(?:/\\d+)?)");

    private SolutionParser() {
    }

    record Entry(String label, Rational value) {
    }

    static List<Entry> parse(String submitted) {
        if (submitted == null) {
            return null;
        }
        List<Entry> entries = new ArrayList<>();
        Matcher matcher = ITEM.matcher(submitted);
        while (matcher.find()) {
            Rational value = Rational.parse(matcher.group(2));
            if (value == null) {
                return null;
            }
            entries.add(new Entry(matcher.group(1), value));
        }
        return entries.isEmpty() ? null : entries;
    }
}
