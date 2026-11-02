package com.chutiji.pool;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.chutiji.common.BizException;
import com.chutiji.module.api.GenRequest;
import com.chutiji.module.api.GeneratedQuestion;
import com.chutiji.module.api.ModuleMeta;
import com.chutiji.module.api.QuestionModule;
import com.chutiji.module.registry.ModuleRegistry;
import com.chutiji.pool.PoolDtos.GeneratePoolView;
import com.chutiji.question.QuestionEntity;
import com.chutiji.question.QuestionRepository;
import com.chutiji.review.KnowledgeKey;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 题库预生成与取用。
 *
 * <p>池题落库时 source='POOL'、expires_at 为 NULL（设计文档 10.3 裁定 1）：池题是资产，
 * 同一道题可以反复抽取，绝不能像实时题那样 24 小时就被清理掉。</p>
 */
@Service
public class PoolService {

    private static final Logger log = LoggerFactory.getLogger(PoolService.class);

    /** 每档默认生成条数。 */
    public static final int DEFAULT_COUNT = 20;

    /** 单档生成上限，防止一次请求把库灌爆。 */
    public static final int MAX_COUNT = 200;

    private final ModuleRegistry registry;
    private final QuestionRepository questionRepository;
    private final PoolPickRepository pickRepository;
    private final ObjectMapper objectMapper;
    private final PoolProperties properties;

    public PoolService(ModuleRegistry registry, QuestionRepository questionRepository,
                       PoolPickRepository pickRepository, ObjectMapper objectMapper,
                       PoolProperties properties) {
        this.registry = registry;
        this.questionRepository = questionRepository;
        this.pickRepository = pickRepository;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    /**
     * 把某一档难度的题预生成进 t_question。
     *
     * <p>单个题生成失败只记 skipped 继续跑：数独的唯一解重试有可能耗尽预算，
     * 一条失败不该让整批白跑。</p>
     */
    @Transactional
    public GeneratePoolView generate(String moduleKey, Map<String, Object> difficulty, int count) {
        QuestionModule module = registry.require(moduleKey);
        ModuleMeta meta = module.meta();
        Map<String, Object> diff = normalize(difficulty);
        int target = clampCount(count);
        String difficultyJson = toJson(diff);

        int generated = 0;
        int skipped = 0;
        for (int i = 0; i < target; i++) {
            try {
                GeneratedQuestion generatedQuestion = module.generate(new GenRequest(meta.key(), diff));
                questionRepository.save(newPoolEntity(meta, difficultyJson, generatedQuestion));
                generated++;
            } catch (RuntimeException ex) {
                skipped++;
                log.warn("池题生成失败 module={} difficulty={} 第 {} 条：{}", meta.key(), difficultyJson, i + 1,
                        ex.getMessage());
            }
        }
        log.info("池题跑批完成 module={} difficulty={} generated={} skipped={}", meta.key(), difficultyJson,
                generated, skipped);
        return new GeneratePoolView(generated, skipped);
    }

    /**
     * 从池里取一道该用户近期没拿过的题，并登记取用流水。
     *
     * <p>返回空表示「池里没有可用题」，调用方自行回退实时生成。全池近期都取过时也返回空：
     * 宁可实时生成一道新的，也不能把重复题塞给用户（任务书约束 6）。</p>
     */
    @Transactional
    public Optional<QuestionEntity> take(Long userId, String moduleKey, Map<String, Object> difficulty) {
        List<QuestionEntity> candidates = availableFor(userId, moduleKey, difficulty);
        if (candidates.isEmpty()) {
            return Optional.empty();
        }
        // 按 (用户, 日期) 定位起点再顺序取：同一用户同一天的取用是稳定的，
        // 但每取一次都会记流水，下一次的候选集少一道，所以连续出题不会撞题。
        int offset = Math.floorMod(userId + LocalDate.now().toEpochDay(), candidates.size());
        QuestionEntity picked = candidates.get(offset);

        PoolPickEntity pick = new PoolPickEntity();
        pick.setUserId(userId);
        pick.setQuestionId(picked.getId());
        pickRepository.save(pick);
        return Optional.of(picked);
    }

    /** 只查不取：给「池里还有没有题」这类判断用，不写流水。 */
    @Transactional(readOnly = true)
    public List<QuestionEntity> availableFor(Long userId, String moduleKey, Map<String, Object> difficulty) {
        LocalDateTime now = LocalDateTime.now();
        Set<String> recent = new HashSet<>(pickRepository.findRecentQuestionIds(userId,
                now.minusDays(Math.max(0, properties.getRecentDays()))));

        List<QuestionEntity> matched = new ArrayList<>();
        for (QuestionEntity entity : questionRepository.findPoolByModule(moduleKey)) {
            if (entity.getExpiresAt() != null && entity.getExpiresAt().isBefore(now)) {
                continue;
            }
            if (!sameDifficulty(entity.getDifficulty(), difficulty)) {
                continue;
            }
            if (recent.contains(entity.getId())) {
                continue;
            }
            matched.add(entity);
        }
        // 按 id 排序，让「取第几个」在同一份池子上是稳定可复现的。
        matched.sort(Comparator.comparing(QuestionEntity::getId));
        return matched;
    }

    /**
     * 难度等价判断：按键排序后逐字段比对。
     *
     * <p>库里存的是当初写进去的 JSON 文本，请求给的是一个 Map，
     * 两者的键序、数值写法都可能不同，所以两边都先归一成规范文本再比。</p>
     */
    boolean sameDifficulty(String storedJson, Map<String, Object> requested) {
        if (storedJson == null || storedJson.isBlank()) {
            return false;
        }
        try {
            Map<String, Object> stored = objectMapper.readValue(storedJson,
                    new TypeReference<LinkedHashMap<String, Object>>() {
                    });
            return KnowledgeKey.canonicalJson(stored).equals(KnowledgeKey.canonicalJson(normalize(requested)));
        } catch (JsonProcessingException ex) {
            return false;
        }
    }

    private QuestionEntity newPoolEntity(ModuleMeta meta, String difficultyJson, GeneratedQuestion generated) {
        QuestionEntity entity = new QuestionEntity();
        entity.setId("q_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16));
        entity.setModuleKey(meta.key());
        entity.setQuestionType(meta.type().name());
        entity.setDifficulty(difficultyJson);
        entity.setPrompt(generated.prompt());
        entity.setPayload(toJson(generated.payload()));
        entity.setAnswer(toJson(generated.answer()));
        entity.setExplanation(generated.explanation());
        entity.setSource("POOL");
        // 池题不属于任何用户，也不过期（设计文档 10.3 裁定 1）
        entity.setUserId(null);
        entity.setExpiresAt(null);
        return entity;
    }

    private int clampCount(int count) {
        int fallback = properties.getDefaultCount() > 0 ? properties.getDefaultCount() : DEFAULT_COUNT;
        int effective = count > 0 ? count : fallback;
        return Math.min(effective, MAX_COUNT);
    }

    private Map<String, Object> normalize(Map<String, Object> difficulty) {
        return difficulty == null ? Map.of() : difficulty;
    }

    private String toJson(Map<String, Object> value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new BizException(5001, "JSON 序列化失败: " + ex.getMessage());
        }
    }
}
