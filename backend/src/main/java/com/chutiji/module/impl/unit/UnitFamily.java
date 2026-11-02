package com.chutiji.module.impl.unit;

import java.util.List;

/** 五个单位族，每族内置完整单位表（因子相对于该族基准单位）。 */
public enum UnitFamily {

    LENGTH("长度", List.of(
            new UnitDef("毫米", 0.001),
            new UnitDef("厘米", 0.01),
            new UnitDef("分米", 0.1),
            new UnitDef("米", 1),
            new UnitDef("千米", 1000),
            new UnitDef("英寸", 0.0254),
            new UnitDef("英尺", 0.3048),
            new UnitDef("英里", 1609.344))),

    WEIGHT("质量", List.of(
            new UnitDef("毫克", 0.000001),
            new UnitDef("克", 0.001),
            new UnitDef("千克", 1),
            new UnitDef("吨", 1000),
            new UnitDef("斤", 0.5),
            new UnitDef("两", 0.05))),

    VOLUME("容积", List.of(
            new UnitDef("毫升", 0.001),
            new UnitDef("立方厘米", 0.001),
            new UnitDef("厘升", 0.01),
            new UnitDef("分升", 0.1),
            new UnitDef("升", 1),
            new UnitDef("立方分米", 1),
            new UnitDef("立方米", 1000))),

    TIME("时间", List.of(
            new UnitDef("毫秒", 0.001),
            new UnitDef("秒", 1),
            new UnitDef("分钟", 60),
            new UnitDef("小时", 3600),
            new UnitDef("天", 86400),
            new UnitDef("周", 604800))),

    AREA("面积", List.of(
            new UnitDef("平方毫米", 0.000001),
            new UnitDef("平方厘米", 0.0001),
            new UnitDef("平方分米", 0.01),
            new UnitDef("平方米", 1),
            new UnitDef("公顷", 10000),
            new UnitDef("平方千米", 1000000)));

    private final String label;
    private final List<UnitDef> units;

    UnitFamily(String label, List<UnitDef> units) {
        this.label = label;
        this.units = units;
    }

    public String label() {
        return label;
    }

    public List<UnitDef> units() {
        return units;
    }

    public UnitDef require(String name) {
        for (UnitDef unit : units) {
            if (unit.name().equals(name)) {
                return unit;
            }
        }
        throw new IllegalArgumentException("未知单位: " + name);
    }

    public static UnitFamily parse(Object raw) {
        if (raw instanceof String text) {
            String trimmed = text.trim();
            for (UnitFamily family : values()) {
                if (family.name().equalsIgnoreCase(trimmed) || family.label.equals(trimmed)) {
                    return family;
                }
            }
        }
        return LENGTH;
    }

}
