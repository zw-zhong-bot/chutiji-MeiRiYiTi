package com.chutiji.module.api;

import java.util.Map;

/** 判题请求：服务端留存的答案 + 用户提交的答案。 */
public record GradeRequest(String moduleKey, Map<String, Object> storedAnswer, String submitted) {
}
