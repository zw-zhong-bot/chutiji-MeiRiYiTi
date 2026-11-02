package com.chutiji.module.api;

import java.util.List;

/**
 * 难度轴定义。由模块自己声明，前端不硬编码任何模块的难度选项。
 * min/max/step 仅 INT_RANGE 使用，options 仅 ENUM 使用。
 */
public record DifficultyAxis(
        String key,
        String label,
        AxisKind kind,
        Integer min,
        Integer max,
        Integer step,
        Object defaultValue,
        List<AxisOption> options) {

    public static DifficultyAxis intRange(String key, String label, int min, int max, int step, int defaultValue) {
        return new DifficultyAxis(key, label, AxisKind.INT_RANGE, min, max, step, defaultValue, null);
    }

    public static DifficultyAxis bool(String key, String label, boolean defaultValue) {
        return new DifficultyAxis(key, label, AxisKind.BOOLEAN, null, null, null, defaultValue, null);
    }

    public static DifficultyAxis enumAxis(String key, String label, String defaultValue, List<AxisOption> options) {
        return new DifficultyAxis(key, label, AxisKind.ENUM, null, null, null, defaultValue, List.copyOf(options));
    }
}
