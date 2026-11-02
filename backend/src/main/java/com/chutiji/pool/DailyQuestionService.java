package com.chutiji.pool;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.chutiji.module.api.DifficultyAxis;
import com.chutiji.module.api.ModuleMeta;
import com.chutiji.module.registry.ModuleRegistry;
import com.chutiji.question.QuestionDtos.QuestionView;
import com.chutiji.question.QuestionEntity;
import com.chutiji.question.QuestionRepository;
import com.chutiji.question.QuestionDtos.CreateQuestionRequest;
import com.chutiji.question.QuestionService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 每日一题：每个自然日、每个用户固定一道题，当天重复调用返回同一道。
 *
 * <p>不重新生成的保证来自 t_daily_question 的 (user_id, daily_date) 唯一键 + 先查后建：
 * 只要当天有记录就原样返回，题目本身是池题（expires_at 为 NULL），不会中途过期。</p>
 */
@Service
public class DailyQuestionService {

    private static final Logger log = LoggerFactory.getLogger(DailyQuestionService.class);

    /** 每日一题轮转的模块顺序，与 ModuleRegistry 的注册顺序无关，固定在这里以保证跨天稳定。 */
    private static final List<String> DAILY_MODULES = List.of(
            "arithmetic", "sudoku", "logic", "algebra", "unit", "geometry");

    private final ModuleRegistry registry;
    private final PoolService poolService;
    private final QuestionService questionService;
    private final QuestionRepository questionRepository;
    private final DailyQuestionRepository dailyRepository;
    private final ObjectMapper objectMapper;

    public DailyQuestionService(ModuleRegistry registry, PoolService poolService,
                                QuestionService questionService, QuestionRepository questionRepository,
                                DailyQuestionRepository dailyRepository, ObjectMapper objectMapper) {
        this.registry = registry;
        this.poolService = poolService;
        this.questionService = questionService;
        this.questionRepository = questionRepository;
        this.dailyRepository = dailyRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public QuestionView today(Long userId) {
        LocalDate date = LocalDate.now();
        Optional<DailyQuestionEntity> existing = dailyRepository.findByUserIdAndDailyDate(userId, date);
        if (existing.isPresent()) {
            return toView(questionRepository.findById(existing.get().getQuestionId()).orElse(null),
                    existing.get().getQuestionId(), pickModule(userId, date));
        }

        String moduleKey = pickModule(userId, date);
        QuestionModuleHolder holder = resolveModule(moduleKey);
        QuestionEntity question = pickOrGenerate(userId, holder);

        DailyQuestionEntity row = new DailyQuestionEntity();
        row.setUserId(userId);
        row.setDailyDate(date);
        row.setQuestionId(question.getId());
        dailyRepository.save(row);

        return toView(question, question.getId(), moduleKey);
    }

    /** 按 user_id + 日期的哈希取模选模块：同一天同一用户稳定，换一天会变。 */
    String pickModule(Long userId, LocalDate date) {
        List<String> available = DAILY_MODULES.stream().filter(key -> registry.find(key).isPresent()).toList();
        if (available.isEmpty()) {
            List<ModuleMeta> metas = registry.metas();
            return metas.isEmpty() ? "arithmetic" : metas.get(0).key();
        }
        int index = Math.floorMod(seed(userId, date), available.size());
        return available.get(index);
    }

    /**
     * 先取池题，池空则实时生成一道并落库。
     *
     * <p>实时兜底生成的是 GENERATED 题（24 小时过期），这对每日一题是安全的：
     * 当天必然在有效期内，跨天已换题。</p>
     */
    private QuestionEntity pickOrGenerate(Long userId, QuestionModuleHolder holder) {
        Map<String, Object> difficulty = holder.difficulty();
        Optional<QuestionEntity> pooled = poolService.take(userId, holder.moduleKey(), difficulty);
        if (pooled.isPresent()) {
            return pooled.get();
        }
        QuestionView created = questionService.create(userId,
                new CreateQuestionRequest(holder.moduleKey(), difficulty));
        return questionRepository.findById(created.questionId())
                .orElseThrow(() -> new IllegalStateException("每日一题生成后取不到: " + created.questionId()));
    }

    private QuestionView toView(QuestionEntity entity, String questionId, String moduleKey) {
        if (entity == null) {
            throw new IllegalStateException("每日一题的题目记录丢失: " + questionId);
        }
        ModuleMeta meta = registry.require(entity.getModuleKey()).meta();
        Map<String, Object> payload = readPayload(entity.getPayload());
        // 下发结构：绝不包含 answer
        return new QuestionView(entity.getId(), meta.key(), meta.type().name(), entity.getPrompt(),
                payload, meta.timeLimitSec(), entity.getExpiresAt());
    }

    private Map<String, Object> readPayload(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<LinkedHashMap<String, Object>>() {
            });
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("每日一题的 payload 解析失败", ex);
        }
    }

    /** 该模块的默认难度：把每个轴的 defaultValue 收成一份难度配置。 */
    private Map<String, Object> defaultDifficulty(ModuleMeta meta) {
        Map<String, Object> difficulty = new LinkedHashMap<>();
        for (DifficultyAxis axis : meta.axes()) {
            if (axis.defaultValue() != null) {
                difficulty.put(axis.key(), axis.defaultValue());
            }
        }
        return difficulty;
    }

    private QuestionModuleHolder resolveModule(String moduleKey) {
        ModuleMeta meta = registry.require(moduleKey).meta();
        return new QuestionModuleHolder(meta.key(), defaultDifficulty(meta));
    }

    private int seed(Long userId, LocalDate date) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest((userId + ":" + date).getBytes(StandardCharsets.UTF_8));
            return ((hash[0] & 0xFF) << 24) | ((hash[1] & 0xFF) << 16) | ((hash[2] & 0xFF) << 8) | (hash[3] & 0xFF);
        } catch (NoSuchAlgorithmException ex) {
            log.warn("SHA-256 不可用，退化为 userId 取模");
            return userId.intValue();
        }
    }

    private record QuestionModuleHolder(String moduleKey, Map<String, Object> difficulty) {
    }
}
