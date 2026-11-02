package com.chutiji.question;

import java.time.LocalDateTime;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** 出题 / 判题出入参。出题响应中不得出现任何答案字段。 */
public final class QuestionDtos {

    private QuestionDtos() {
    }

    public record CreateQuestionRequest(
            @NotBlank(message = "moduleKey 不能为空") String moduleKey,
            Map<String, Object> difficulty) {
    }

    /** 下发结构：只有 prompt / payload / 元信息，没有 answer。 */
    public record QuestionView(
            String questionId,
            String moduleKey,
            String type,
            String prompt,
            Map<String, Object> payload,
            int timeLimitSec,
            LocalDateTime expiresAt) {
    }

    /**
     * @param ink 手写笔迹（设计文档 7.4）：客户端原样上送的 {"width","points":[{x,y,pressure,t}]} 数组。
     *            只做留档，服务端不做任何识别或校验，因此用 JsonNode 兜住任意形状。
     */
    public record GradeAnswerRequest(
            @NotNull(message = "answer 不能为空") String answer,
            Integer durationMs,
            JsonNode ink) {
    }

    public record GradeView(boolean correct, String correctAnswer, String explanation) {
    }
}
