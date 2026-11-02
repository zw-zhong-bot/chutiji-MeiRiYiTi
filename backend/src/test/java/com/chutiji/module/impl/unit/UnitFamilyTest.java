package com.chutiji.module.impl.unit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class UnitFamilyTest {

    @Test
    @DisplayName("五族单位表：每族至少 4 个单位，单位名不重复，因子为正，且含基准单位 1")
    void tablesAreComplete() {
        for (UnitFamily family : UnitFamily.values()) {
            List<UnitDef> units = family.units();
            assertTrue(units.size() >= 4, family + " 单位表过少");
            Set<String> names = new HashSet<>();
            boolean hasBase = false;
            for (UnitDef unit : units) {
                assertTrue(names.add(unit.name()), family + " 单位重复: " + unit.name());
                assertTrue(unit.factor() > 0, family + " 因子必须为正: " + unit.name());
                if (unit.factor() == 1.0) {
                    hasBase = true;
                }
            }
            assertTrue(hasBase, family + " 缺少基准单位（因子 1）");
        }
        assertEquals(5, UnitFamily.values().length);
    }

    @Test
    @DisplayName("单位表换算关系与常识一致：1 米=100 厘米、1 千克=2 斤、1 升=1000 毫升、1 小时=60 分钟、1 公顷=10000 平方米")
    void knownConversions() {
        assertConversion(UnitFamily.LENGTH, "米", 100.0, "厘米");
        assertConversion(UnitFamily.LENGTH, "千米", 1000.0, "米");
        assertConversion(UnitFamily.LENGTH, "英里", 1609.344, "米");
        assertConversion(UnitFamily.WEIGHT, "千克", 2.0, "斤");
        assertConversion(UnitFamily.WEIGHT, "吨", 1000.0, "千克");
        assertConversion(UnitFamily.VOLUME, "升", 1000.0, "毫升");
        assertConversion(UnitFamily.VOLUME, "立方米", 1000.0, "升");
        assertConversion(UnitFamily.TIME, "小时", 60.0, "分钟");
        assertConversion(UnitFamily.TIME, "天", 24.0, "小时");
        assertConversion(UnitFamily.AREA, "公顷", 10000.0, "平方米");
        assertConversion(UnitFamily.AREA, "平方千米", 100.0, "公顷");
    }

    @Test
    @DisplayName("未知单位与非法族名：改换算抛异常，解析轴值回退到长度族")
    void unknownValues() {
        assertThrows(IllegalArgumentException.class, () -> UnitFamily.LENGTH.require("光年"));
        assertEquals(UnitFamily.LENGTH, UnitFamily.parse(null));
        assertEquals(UnitFamily.LENGTH, UnitFamily.parse("XXX"));
        assertEquals(UnitFamily.AREA, UnitFamily.parse("area"));
        assertEquals(UnitFamily.TIME, UnitFamily.parse("时间"));
    }

    private void assertConversion(UnitFamily family, String from, double expected, String to) {
        double actual = family.require(from).factor() / family.require(to).factor();
        assertEquals(expected, actual, 1e-9, from + " → " + to);
    }
}
