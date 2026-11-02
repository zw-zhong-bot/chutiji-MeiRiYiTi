package com.chutiji.module.impl.algebra;

/** 方程次数轴：一次 / 二次。 */
public enum Degree {

    LINEAR("一次"),
    QUADRATIC("二次");

    private final String label;

    Degree(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public static Degree parse(Object raw) {
        if (raw instanceof String text) {
            String trimmed = text.trim();
            for (Degree degree : values()) {
                if (degree.name().equalsIgnoreCase(trimmed) || degree.label.equals(trimmed)) {
                    return degree;
                }
            }
        }
        return LINEAR;
    }
}
