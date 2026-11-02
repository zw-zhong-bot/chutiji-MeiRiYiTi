package com.chutiji.question;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.chutiji.module.api.GradeRequest;
import com.chutiji.module.api.GradeResult;
import com.chutiji.module.api.QuestionModule;
import com.chutiji.module.registry.ModuleRegistry;
import com.chutiji.review.ReviewService;
import com.chutiji.question.QuestionDtos.GradeAnswerRequest;
import com.chutiji.question.QuestionDtos.GradeView;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/** 判题只在服务端进行：按 questionId 取出答案比对。 */
@Service
public class GradeService {

    private final ModuleRegistry registry;
    private final QuestionService questionService;
    private final AnswerRecordRepository answerRecordRepository;
    private final ObjectMapper objectMapper;
    private final ReviewService reviewService;

    public GradeService(ModuleRegistry registry, QuestionService questionService,
                        AnswerRecordRepository answerRecordRepository, ObjectMapper objectMapper,
                        ReviewService reviewService) {
        this.registry = registry;
        this.questionService = questionService;
        this.answerRecordRepository = answerRecordRepository;
        this.objectMapper = objectMapper;
        this.reviewService = reviewService;
    }

    @Transactional
    public GradeView grade(Long userId, String questionId, GradeAnswerRequest request) {
        QuestionService.LoadedQuestion loaded = questionService.load(questionId);
        QuestionEntity entity = loaded.entity();
        QuestionModule module = registry.require(entity.getModuleKey());

        GradeResult result = module.grade(new GradeRequest(entity.getModuleKey(), loaded.answer(), request.answer()));

        AnswerRecordEntity record = new AnswerRecordEntity();
        record.setUserId(userId);
        record.setQuestionId(entity.getId());
        record.setModuleKey(entity.getModuleKey());
        record.setSubmitted(toJson(request.answer()));
        record.setCorrect(result.correct());
        record.setDurationMs(request.durationMs());
        answerRecordRepository.save(record);

        // 判题完成后回写复习卡（设计文档 10.3 裁定 2.4）：答错建卡/更新，答对且已有卡则更新。
        reviewService.recordAnswer(userId, entity.getModuleKey(), entity.getDifficulty(),
                result.correct(), request.durationMs(), java.time.LocalDateTime.now());

        return new GradeView(result.correct(), result.correctAnswer(), result.explanation());
    }

    private String toJson(String answer) {
        try {
            Map<String, Object> wrapper = new LinkedHashMap<>();
            wrapper.put("answer", answer);
            return objectMapper.writeValueAsString(wrapper);
        } catch (JsonProcessingException ex) {
            return "{\"answer\":\"\"}";
        }
    }
}
