package com.chutiji.review;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.chutiji.common.BizException;
import com.chutiji.question.QuestionDtos.CreateQuestionRequest;
import com.chutiji.question.QuestionDtos.QuestionView;
import com.chutiji.question.QuestionService;
import com.chutiji.review.ReviewDtos.ReviewCardView;
import com.chutiji.review.ReviewDtos.ReviewStartView;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 复习引擎的业务层：答错建卡 / 答对更新 / 到期列表 / 到期重新生成同类新题。
 *
 * <p>只有答错才建卡；答对且没有卡时什么都不做（没做错过就不进复习队列）。
 */
@Service
public class ReviewService {

    private final ReviewCardRepository cardRepository;
    private final ReviewLogRepository logRepository;
    private final ReviewDurationSampleRepository durationRepository;
    private final QuestionService questionService;
    private final ObjectMapper objectMapper;
    private final FsrsScheduler scheduler = new FsrsScheduler();

    public ReviewService(ReviewCardRepository cardRepository,
                         ReviewLogRepository logRepository,
                         ReviewDurationSampleRepository durationRepository,
                         QuestionService questionService,
                         ObjectMapper objectMapper) {
        this.cardRepository = cardRepository;
        this.logRepository = logRepository;
        this.durationRepository = durationRepository;
        this.questionService = questionService;
        this.objectMapper = objectMapper;
    }

    /**
     * 判题后回写复习状态。correct=false 时按 rating=1(Again) 建卡或更新；
     * correct=true 时只有在卡已存在的情况下才按用时映射 Hard/Good/Easy 更新。
     */
    @Transactional
    public void recordAnswer(Long userId, String moduleKey, String difficultyJson,
                             boolean correct, Integer durationMs, LocalDateTime now) {
        if (userId == null || moduleKey == null || moduleKey.isBlank()) {
            return;
        }
        Map<String, Object> difficulty = readDifficulty(difficultyJson);
        String knowledgeKey = KnowledgeKey.of(moduleKey, difficulty);
        ReviewCardEntity card = cardRepository.findByUserIdAndKnowledgeKey(userId, knowledgeKey).orElse(null);

        if (!correct) {
            int rating = FsrsScheduler.AGAIN;
            if (card == null) {
                card = newCard(userId, moduleKey, knowledgeKey, difficulty, now);
            }
            applyRating(card, rating, now);
        } else if (card != null) {
            List<Integer> samples = durationRepository.findCorrectDurations(userId, moduleKey, card.getDifficulty());
            applyRating(card, ReviewRatingPolicy.rateForCorrect(samples, durationMs), now);
        }
    }

    @Transactional(readOnly = true)
    public List<ReviewCardView> dueCards(Long userId, LocalDateTime now) {
        return cardRepository.findByUserIdAndDueAtLessThanEqualOrderByDueAtAsc(userId, now).stream()
                .map(card -> toView(card, now))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ReviewCardView> allCards(Long userId, LocalDateTime now) {
        return cardRepository.findByUserIdOrderByDueAtAsc(userId).stream()
                .map(card -> toView(card, now))
                .toList();
    }

    /**
     * 用卡片存的难度配置重新生成一道新题。难度原样透传给模块，模块内部不再随机，
     * 否则复习会失去针对性（设计文档 10.3 风险表）。
     */
    @Transactional
    public ReviewStartView start(Long userId, Long cardId, LocalDateTime now) {
        ReviewCardEntity card = cardRepository.findByIdAndUserId(cardId, userId)
                .orElseThrow(() -> new BizException(2010, "复习卡不存在"));
        Map<String, Object> difficulty = readDifficulty(card.getDifficulty());
        QuestionView question = questionService.create(userId, new CreateQuestionRequest(card.getModuleKey(), difficulty));
        return new ReviewStartView(card.getId(), card.getModuleKey(), difficulty, question.questionId(),
                question.prompt(), question.payload(), question.type(), question.timeLimitSec(),
                question.expiresAt());
    }

    // ---------------------------------------------------------------- 内部

    private ReviewCardEntity newCard(Long userId, String moduleKey, String knowledgeKey,
                                     Map<String, Object> difficulty, LocalDateTime now) {
        ReviewCardEntity card = new ReviewCardEntity();
        card.setUserId(userId);
        card.setModuleKey(moduleKey);
        card.setKnowledgeKey(knowledgeKey);
        card.setDifficulty(writeJson(difficulty));
        card.setStability(0.0);
        card.setDifficultyFsrs(FsrsScheduler.DEFAULT_DIFFICULTY);
        card.setReps(0);
        card.setLapses(0);
        card.setState(FsrsState.NEW);
        card.setDueAt(now);
        return cardRepository.save(card);
    }

    private void applyRating(ReviewCardEntity card, int rating, LocalDateTime now) {
        FsrsState before = new FsrsState(
                card.getStability() == null ? 0.0 : card.getStability(),
                card.getDifficultyFsrs() == null ? FsrsScheduler.DEFAULT_DIFFICULTY : card.getDifficultyFsrs(),
                card.getReps() == null ? 0 : card.getReps(),
                card.getLapses() == null ? 0 : card.getLapses(),
                card.getState() == null ? FsrsState.NEW : card.getState());

        double elapsedDays = elapsedDays(card.getLastReviewAt(), now);
        FsrsResult result = scheduler.schedule(before, rating, now, elapsedDays);
        FsrsState next = result.next();

        card.setStability(next.stability());
        card.setDifficultyFsrs(next.difficultyFsrs());
        card.setReps(next.reps());
        card.setLapses(next.lapses());
        card.setState(next.state());
        card.setDueAt(now.plusSeconds(Math.round(result.intervalDays() * 86_400)));
        card.setLastReviewAt(now);
        cardRepository.save(card);

        ReviewLogEntity log = new ReviewLogEntity();
        log.setCardId(card.getId());
        log.setRating(rating);
        log.setSBefore(before.stability());
        log.setDBefore(before.difficultyFsrs());
        log.setSAfter(next.stability());
        log.setDAfter(next.difficultyFsrs());
        log.setIntervalDays(result.intervalDays());
        logRepository.save(log);
    }

    private double elapsedDays(LocalDateTime lastReviewAt, LocalDateTime now) {
        if (lastReviewAt == null) {
            return 0.0;
        }
        long seconds = Duration.between(lastReviewAt, now).getSeconds();
        return seconds <= 0 ? 0.0 : seconds / 86_400.0;
    }

    private ReviewCardView toView(ReviewCardEntity card, LocalDateTime now) {
        return new ReviewCardView(
                card.getId(),
                card.getModuleKey(),
                readDifficulty(card.getDifficulty()),
                card.getKnowledgeKey(),
                card.getStability() == null ? 0.0 : card.getStability(),
                card.getDifficultyFsrs() == null ? FsrsScheduler.DEFAULT_DIFFICULTY : card.getDifficultyFsrs(),
                card.getReps() == null ? 0 : card.getReps(),
                card.getLapses() == null ? 0 : card.getLapses(),
                card.getState(),
                card.getDueAt(),
                card.getLastReviewAt(),
                card.getDueAt() != null && !card.getDueAt().isAfter(now));
    }

    private Map<String, Object> readDifficulty(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            Map<String, Object> parsed = objectMapper.readValue(json, new TypeReference<LinkedHashMap<String, Object>>() {
            });
            return parsed == null ? Map.of() : parsed;
        } catch (JsonProcessingException ex) {
            throw new BizException(5001, "难度配置解析失败: " + ex.getMessage());
        }
    }

    private String writeJson(Map<String, Object> value) {
        try {
            return objectMapper.writeValueAsString(value == null ? Map.of() : value);
        } catch (JsonProcessingException ex) {
            throw new BizException(5001, "难度配置序列化失败: " + ex.getMessage());
        }
    }
}
