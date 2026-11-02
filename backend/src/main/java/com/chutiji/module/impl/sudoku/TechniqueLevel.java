package com.chutiji.module.impl.sudoku;

/** 技巧等级，序数即难度序：解出一道题所需的最难技巧决定它的等级。 */
public enum TechniqueLevel {

    EASY("EASY", "简单"),
    MEDIUM("MEDIUM", "中等"),
    HARD("HARD", "困难"),
    EXPERT("EXPERT", "专家");

    private final String code;
    private final String label;

    TechniqueLevel(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public String code() {
        return code;
    }

    public String label() {
        return label;
    }

    /** 从难度轴取值解析，非法值回落到 EASY。 */
    public static TechniqueLevel parse(Object raw) {
        if (raw == null) {
            return EASY;
        }
        String text = raw.toString().trim().toUpperCase();
        for (TechniqueLevel level : values()) {
            if (level.code.equals(text)) {
                return level;
            }
        }
        return EASY;
    }
}
