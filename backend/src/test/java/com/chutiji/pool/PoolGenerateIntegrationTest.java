package com.chutiji.pool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 题库跑批：条数、落库来源与有效期，以及跑批接口本身。 */
@SpringBootTest(properties = {"app.question.cleanup-cron=-", "app.pool.schedule-enabled=false"})
@AutoConfigureMockMvc
@Transactional
class PoolGenerateIntegrationTest {

    @Autowired
    private PoolService poolService;

    @Autowired
    private QuestionRepository questionRepository;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PoolPickRepository pickRepository;

    /**
     * 每个测试从干净的池开始：库里可能残留真实跑批留下的池题，断言不能依赖「库里本来没有池题」。
     * 删除发生在测试事务内，结束后随事务回滚，不影响库里的既有数据。
     */
    @BeforeEach
    void resetPool() {
        pickRepository.deleteAll();
        questionRepository.deleteAll(questionRepository.findPoolByModule("arithmetic"));
    }

    private static final Map<String, Object> DIFFICULTY = LinkedHashMap.newLinkedHashMap(4);

    static {
        DIFFICULTY.put("maxOperand", 100);
        DIFFICULTY.put("operatorCount", 2);
        DIFFICULTY.put("allowParentheses", false);
        DIFFICULTY.put("allowDivision", true);
    }

    private String tokenFor(String username) {
        UserEntity user = new UserEntity();
        user.setUsername(username);
        user.setPasswordHash("x");
        user.setNickname(username);
        user.setRole("USER");
        user.setEnabled(Boolean.TRUE);
        Long id = userRepository.save(user).getId();
        return jwtService.issue(id, username).token();
    }

    @Test
    @DisplayName("跑批生成指定条数，全部 source=POOL 且 expires_at 为 NULL")
    void generatesPoolQuestionsWithNoExpiry() {
        PoolDtos.GeneratePoolView view = poolService.generate("arithmetic", DIFFICULTY, 20);

        assertEquals(20, view.generated(), "生成的条数必须与请求一致");
        assertEquals(0, view.skipped(), "口算题不该有失败");

        List<QuestionEntity> pool = questionRepository.findPoolByModule("arithmetic");
        assertEquals(20, pool.size(), "池题应全部落库且只有这 20 道");
        for (QuestionEntity entity : pool) {
            assertEquals("POOL", entity.getSource(), "池题的 source 必须是 POOL");
            assertNull(entity.getExpiresAt(), "池题的 expires_at 必须是 NULL（设计文档 10.3 裁定 1）");
            assertNull(entity.getUserId(), "池题不属于任何用户");
            assertNotNull(entity.getDifficulty(), "池题必须记下难度配置");
        }
        System.out.printf("[池题跑批] generated=%d skipped=%d，样例 id=%s source=%s expires_at=%s%n",
                view.generated(), view.skipped(), pool.get(0).getId(), pool.get(0).getSource(),
                pool.get(0).getExpiresAt());
    }

    @Test
    @DisplayName("request.count 为空时用默认 20 道")
    void defaultCountIsTwenty() {
        assertEquals(PoolService.DEFAULT_COUNT, 20);
        int clamped = Math.min(0 > 0 ? 0 : PoolService.DEFAULT_COUNT, PoolService.MAX_COUNT);
        assertEquals(20, clamped);
    }

    @Test
    @DisplayName("POST /api/pool/generate 返回 generated / skipped")
    void generateEndpointReturnsCounts() throws Exception {
        String token = tokenFor("pool_api_" + java.util.UUID.randomUUID().toString().substring(0, 8));
        String body = objectMapper.writeValueAsString(
                Map.of("moduleKey", "arithmetic", "difficulty", DIFFICULTY, "count", 5));

        String response = mockMvc.perform(post("/api/pool/generate")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        System.out.println("[POST /api/pool/generate] " + response);
        JsonNode data = objectMapper.readTree(response).path("data");
        assertEquals(5, data.path("generated").asInt());
        assertEquals(0, data.path("skipped").asInt());
        assertEquals(5, questionRepository.findPoolByModule("arithmetic").size());
    }

    @Test
    @DisplayName("难度键序无关：{a:1,b:2} 与 {b:2,a:1} 命中同一批池题")
    void difficultyKeyOrderDoesNotMatter() {
        poolService.generate("arithmetic", DIFFICULTY, 4);

        Map<String, Object> reversed = LinkedHashMap.newLinkedHashMap(4);
        reversed.put("allowDivision", true);
        reversed.put("allowParentheses", false);
        reversed.put("operatorCount", 2);
        reversed.put("maxOperand", 100);

        List<QuestionEntity> matched = poolService.availableFor(1L, "arithmetic", reversed);
        assertEquals(4, matched.size(), "键序不同的同一档难度必须命中同一批池题");

        Map<String, Object> other = LinkedHashMap.newLinkedHashMap(4);
        other.put("maxOperand", 1000);
        other.put("operatorCount", 3);
        other.put("allowParentheses", true);
        other.put("allowDivision", false);
        assertTrue(poolService.availableFor(1L, "arithmetic", other).isEmpty(), "换一档难度不该命中");
    }

    @Test
    @DisplayName("数值写法不同也算同一档：100 与 100.0")
    void difficultyNumberFormDoesNotMatter() {
        poolService.generate("arithmetic", DIFFICULTY, 2);

        Map<String, Object> decimals = LinkedHashMap.newLinkedHashMap(4);
        decimals.put("maxOperand", 100.0);
        decimals.put("operatorCount", 2.0);
        decimals.put("allowParentheses", false);
        decimals.put("allowDivision", true);

        assertEquals(2, poolService.availableFor(1L, "arithmetic", decimals).size(),
                "100 与 100.0 是同一档难度");
    }

    @Test
    @DisplayName("跑批接口需要登录")
    void generateRequiresAuth() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("moduleKey", "arithmetic", "count", 1));
        mockMvc.perform(post("/api/pool/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized());
    }
}
