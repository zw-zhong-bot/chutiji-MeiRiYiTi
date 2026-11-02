package com.chutiji.module.impl.unit;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 测试侧独立验算：只依赖题面文本与单位表，不复用 UnitModule 的生成逻辑。 */
final class UnitCheckSupport {

    private static final Pattern PROMPT = Pattern.compile(
            "^单位换算：([-+]?\\d+(?:\\.\\d+)?)\\s+(\\S+)\\s*=\\s*\\?\\s*(\\S+)$");

    private UnitCheckSupport() {
    }

    static Matcher parsePrompt(String prompt) {
        Matcher matcher = PROMPT.matcher(prompt);
        if (!matcher.matches()) {
            throw new AssertionError("题面格式无法解析: " + prompt);
        }
        return matcher;
    }

    /** 从题面取出 (源数值, 源单位, 目标单位)，用单位表重算目标数值。 */
    static double recompute(String prompt, UnitFamily family, String expectedTargetUnit) {
        Matcher matcher = parsePrompt(prompt);
        double sourceValue = Double.parseDouble(matcher.group(1));
        String sourceUnit = matcher.group(2);
        String targetUnit = matcher.group(3);
        if (!targetUnit.equals(expectedTargetUnit)) {
            throw new AssertionError("payload.unit 与题面目标单位不一致: " + targetUnit + " vs " + expectedTargetUnit);
        }
        double from = factor(family, sourceUnit);
        double to = factor(family, targetUnit);
        // 用 BigDecimal 独立重算，避免把 double 尾数垃圾当成正确答案
        return java.math.BigDecimal.valueOf(sourceValue)
                .multiply(java.math.BigDecimal.valueOf(from))
                .divide(java.math.BigDecimal.valueOf(to), 12, java.math.RoundingMode.HALF_UP)
                .stripTrailingZeros()
                .doubleValue();
    }

    /** 题面两单位的换算比（大/小）。 */
    static double ratio(String prompt, UnitFamily family) {
        Matcher matcher = parsePrompt(prompt);
        double from = factor(family, matcher.group(2));
        double to = factor(family, matcher.group(3));
        return Math.max(from, to) / Math.min(from, to);
    }

    private static double factor(UnitFamily family, String name) {
        List<UnitDef> units = family.units();
        for (UnitDef unit : units) {
            if (unit.name().equals(name)) {
                return unit.factor();
            }
        }
        throw new AssertionError("题面出现 " + family + " 族之外的未知单位: " + name);
    }
}
