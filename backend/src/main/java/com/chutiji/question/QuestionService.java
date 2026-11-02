package com.chutiji.question;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.chutiji.common.BizException;
import com.chutiji.module.api.GenRequest;
import com.chutiji.module.api.GeneratedQuestion;
import com.chutiji.module.api.ModuleMeta;
import com.chutiji.module.api.QuestionModule;
import com.chutiji.module.registry.ModuleRegistry;
import com.chutiji.pool.PoolService;
import com.chutiji.question.QuestionDtos.CreateQuestionRequest;
import com.chutiji.question.QuestionDtos.QuestionView;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class QuestionService {

    /** 实时生成的题 24 小时过期（设计文档 10.3 裁定 1）。 */
    private static final int EXPIRE_HOURS = 24;

    private final ModuleRegistry registry;
    private final QuestionRepository questionRepository;
    private final ObjectMapper objectMapper;
    private final PoolService poolService;

    public QuestionService(ModuleRegistry registry, QuestionRepository questionRepository, ObjectMapper objectMapper,
                           PoolService poolService) {
        this.registry = registry;
        this.questionRepository = questionRepository;
        this.objectMapper = objectMapper;
        this.poolService = poolService;
    }

    @Transactional
    public QuestionView create(Long userId, CreateQuestionRequest request) {
        QuestionModule module = registry.require(request.moduleKey());
        ModuleMeta meta = module.meta();
        Map<String, Object> difficulty = request.difficulty() == null ? Map.of() : request.difficulty();

        // 优先取池题（设计文档 3.3）：池里没有可用题才回退实时生成。
        // 池题是公共资产，取用后仍留在表里服务其他用户。
        Optional<QuestionEntity> pooled = poolService.take(userId, meta.key(), difficulty);
        if (pooled.isPresent()) {
            return toView(pooled.get(), meta);
        }

        GeneratedQuestion generated = module.generate(new GenRequest(meta.key(), difficulty));

        String id = "q_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        QuestionEntity entity = new QuestionEntity();
        entity.setId(id);
        entity.setModuleKey(meta.key());
        entity.setQuestionType(meta.type().name());
        entity.setDifficulty(toJson(difficulty));
        entity.setPrompt(generated.prompt());
        entity.setPayload(toJson(generated.payload()));
        entity.setAnswer(toJson(generated.answer()));
        entity.setExplanation(generated.explanation());
        entity.setSource("GENERATED");
        entity.setUserId(userId);
        entity.setExpiresAt(LocalDateTime.now().plusHours(EXPIRE_HOURS));
        questionRepository.save(entity);

        // 下发结构：绝不包含 answer
        Map<String, Object> payload = new LinkedHashMap<>(generated.payload());
        return new QuestionView(id, meta.key(), meta.type().name(), generated.prompt(),
                payload, meta.timeLimitSec(), entity.getExpiresAt());
    }

    /** 池题的落库行转下发结构：payload 读库，answer 留在服务端。 */
    private QuestionView toView(QuestionEntity entity, ModuleMeta meta) {
        return new QuestionView(entity.getId(), meta.key(), meta.type().name(), entity.getPrompt(),
                readJson(entity.getPayload()), meta.timeLimitSec(), entity.getExpiresAt());
    }

    @Transactional(readOnly = true)
    public LoadedQuestion load(String questionId) {
        QuestionEntity entity = questionRepository.findById(questionId)
                .orElseThrow(() -> new BizException(2008, "题目不存在"));
        if (entity.getExpiresAt() != null && entity.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BizException(2009, "题目已过期");
        }
        Map<String, Object> answer = readJson(entity.getAnswer());
        return new LoadedQuestion(entity, answer);
    }

    public record LoadedQuestion(QuestionEntity entity, Map<String, Object> answer) {
    }

    private String toJson(Map<String, Object> value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new BizException(5001, "JSON 序列化失败: " + ex.getMessage());
        }
    }

    private Map<String, Object> readJson(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {
            });
        } catch (JsonProcessingException ex) {
            throw new BizException(5001, "JSON 反序列化失败: " + ex.getMessage());
        }
    }
}
