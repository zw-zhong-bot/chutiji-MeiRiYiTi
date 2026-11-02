package com.chutiji.module.api;

import java.util.List;

/** 模块元信息：名称、题型、难度轴定义。 */
public record ModuleMeta(
        String key,
        String name,
        String description,
        QuestionType type,
        List<DifficultyAxis> axes,
        boolean poolBased,
        int timeLimitSec) {
}
