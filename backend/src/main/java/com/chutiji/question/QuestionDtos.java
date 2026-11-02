package com.chutiji.question;

import java.time.LocalDateTime;
import java.util.Map;

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

    public record GradeAnswerRequest(
            @NotNull(message = "answer 不能为空") String answer,
            Integer durationMs) {
    }

    public record GradeView(boolean correct, String correctAnswer, String explanation) {
    }
}
