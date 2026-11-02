package com.chutiji.module.api;

import java.util.Map;

/** 出题请求：模块 key + 本次难度轴取值。 */
public record GenRequest(String moduleKey, Map<String, Object> difficulty) {

    public GenRequest {
        difficulty = difficulty == null ? Map.of() : Map.copyOf(difficulty);
    }
}
