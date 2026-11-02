package com.chutiji.module.api;

import java.util.Map;

/**
 * 模块出题结果。answer 只在服务端流转，绝不下发到客户端。
 */
public record GeneratedQuestion(
        String prompt,
        Map<String, Object> payload,
        Map<String, Object> answer,
        String explanation) {
}
