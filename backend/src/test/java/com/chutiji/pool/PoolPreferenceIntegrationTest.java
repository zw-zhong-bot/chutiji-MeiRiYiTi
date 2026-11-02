package com.chutiji.pool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.chutiji.question.QuestionEntity;
import com.chutiji.question.QuestionRepository;
import com.chutiji.security.JwtService;
import com.chutiji.user.UserEntity;
import com.chutiji.user.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/** 出题优先取池题：池里有就走池、池空回退实时生成、同一用户近期不重复。 */
@SpringBootTest(properties = {"app.question.cleanup-cron=-", "app.pool.schedule-enabled=false"})
@AutoConfigureMockMvc
@Transactional
class PoolPreferenceIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PoolService poolService;

    @Autowired
    private QuestionRepository questionRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PoolPickRepository pickRepository;

    private static final Map<String, Object> DIFFICULTY = LinkedHashMap.newLinkedHashMap(4);

    static {
        DIFFICULTY.put("maxOperand", 100);
        DIFFICULTY.put("operatorCount", 2);
        DIFFICULTY.put("allowParentheses", false);
        DIFFICULTY.put("allowDivision", true);
    }

    private Long userId;
    private String token;

    @BeforeEach
    void setUp() {
        // 池与取用流水都从干净状态开始：库里可能残留真实跑批数据，断言不能依赖「池本来是空的」。
        // 删除发生在测试事务内，结束后随事务回滚。
        pickRepository.deleteAll();
        questionRepository.deleteAll(questionRepository.findPoolByModule("arithmetic"));

        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String username = "pool_pick_" + suffix;
        UserEntity user = new UserEntity();
        user.setUsername(username);
        user.setPasswordHash("x");
        user.setNickname(username);
        user.setRole("USER");
        user.setEnabled(Boolean.TRUE);
        userId = userRepository.save(user).getId();
        token = jwtService.issue(userId, username).token();
    }

    private JsonNode createQuestion() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("moduleKey", "arithmetic", "difficulty", DIFFICULTY));
        String response = mockMvc.perform(post("/api/questions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).path("data");
    }

    @Test
    @DisplayName("池里有题时出题返回池题，且不下发答案")
    void prefersPoolQuestion() throws Exception {
        poolService.generate("arithmetic", DIFFICULTY, 3);

        JsonNode data = createQuestion();
        String questionId = data.path("questionId").asText();

        QuestionEntity entity = questionRepository.findById(questionId).orElseThrow();
        assertEquals("POOL", entity.getSource(), "池里有题就必须走池题");
        assertNull(entity.getExpiresAt(), "池题没有有效期");
        assertEquals(entity.getPrompt(), data.path("prompt").asText());
        assertTrue(!data.has("answer"), "响应不得包含 answer");
        assertTrue(!data.path("payload").has("value"), "payload 不得泄露答案");
        System.out.println("[优先取池] questionId=" + questionId + " source=" + entity.getSource());
    }

    @Test
    @DisplayName("池空时回退实时生成，落库 source=GENERATED 且 24 小时过期")
    void fallsBackToGenerated() throws Exception {
        JsonNode data = createQuestion();
        String questionId = data.path("questionId").asText();

        QuestionEntity entity = questionRepository.findById(questionId).orElseThrow();
        assertEquals("GENERATED", entity.getSource(), "池空时必须回退实时生成");
        assertNotNull(entity.getExpiresAt(), "实时题必须有 24 小时有效期");
        assertEquals(userId, entity.getUserId(), "实时题归属出题的那个用户");
        System.out.println("[池空回退] questionId=" + questionId + " source=" + entity.getSource()
                + " expires_at=" + entity.getExpiresAt());
    }

    @Test
    @DisplayName("同一用户连续取 5 次拿到 5 道不同的池题")
    void sameUserDoesNotRepeatPoolQuestion() throws Exception {
        poolService.generate("arithmetic", DIFFICULTY, 5);

        List<String> ids = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            ids.add(createQuestion().path("questionId").asText());
        }
        System.out.println("[连续取池] " + ids);

        assertEquals(5, new java.util.HashSet<>(ids).size(), "同一用户不能重复拿到同一道池题");
        for (String id : ids) {
            assertEquals("POOL", questionRepository.findById(id).orElseThrow().getSource());
        }
    }

    @Test
    @DisplayName("池题取完（都被该用户拿过）后回退实时生成，不会塞重复题")
    void exhaustedPoolFallsBackInsteadOfRepeating() throws Exception {
        poolService.generate("arithmetic", DIFFICULTY, 2);

        List<String> ids = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            ids.add(createQuestion().path("questionId").asText());
        }

        long poolHits = ids.stream()
                .map(id -> questionRepository.findById(id).orElseThrow().getSource())
                .filter("POOL"::equals)
                .count();
        assertEquals(2, poolHits, "池里只有 2 道，只该取到 2 道池题");
        assertEquals(4, new java.util.HashSet<>(ids).size(), "回退生成的题也不能重复");
        System.out.println("[池取尽] " + ids);
    }

    @Test
    @DisplayName("池题被取用后仍留在表里，可服务其他用户")
    void poolQuestionStaysAfterBeingTaken() throws Exception {
        poolService.generate("arithmetic", DIFFICULTY, 1);
        String pooledId = questionRepository.findPoolByModule("arithmetic").get(0).getId();

        String taken = createQuestion().path("questionId").asText();
        assertEquals(pooledId, taken);

        QuestionEntity still = questionRepository.findById(pooledId).orElseThrow();
        assertEquals("POOL", still.getSource(), "池题取用后仍保留在表里");
        assertEquals(1, questionRepository.findPoolByModule("arithmetic").size(), "取用不该把池题删掉");
    }
}
