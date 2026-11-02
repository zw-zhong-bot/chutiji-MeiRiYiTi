package com.chutiji.pool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.chutiji.question.QuestionEntity;
import com.chutiji.question.QuestionRepository;
import com.chutiji.security.JwtService;
import com.chutiji.user.UserEntity;
import com.chutiji.user.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/** 每日一题：同一天同一用户固定、不同用户不同题、答案不下发。 */
@SpringBootTest(properties = {"app.question.cleanup-cron=-", "app.pool.schedule-enabled=false"})
@AutoConfigureMockMvc
@Transactional
class DailyQuestionIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DailyQuestionService dailyQuestionService;

    @Autowired
    private DailyQuestionRepository dailyRepository;

    @Autowired
    private PoolPickRepository pickRepository;

    @Autowired
    private QuestionRepository questionRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private ObjectMapper objectMapper;

    private Long userAId;
    private Long userBId;
    private String tokenA;
    private String tokenB;

    @BeforeEach
    void setUp() {
        // 池与取用流水清空：库里可能残留真实跑批数据，断言不能依赖「池本来是空的」。
        // 删除发生在测试事务内，结束后随事务回滚。
        pickRepository.deleteAll();
        questionRepository.deleteAll(questionRepository.findPoolByModule("arithmetic"));

        String suffix = UUID.randomUUID().toString().substring(0, 8);
        userAId = saveUser("daily_a_" + suffix);
        userBId = saveUser("daily_b_" + suffix);
        tokenA = jwtService.issue(userAId, "daily_a_" + suffix).token();
        tokenB = jwtService.issue(userBId, "daily_b_" + suffix).token();
    }

    private Long saveUser(String username) {
        UserEntity user = new UserEntity();
        user.setUsername(username);
        user.setPasswordHash("x");
        user.setNickname(username);
        user.setRole("USER");
        user.setEnabled(Boolean.TRUE);
        return userRepository.save(user).getId();
    }

    private JsonNode fetchDaily(String token) throws Exception {
        String response = mockMvc.perform(get("/api/daily").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).path("data");
    }

    @Test
    @DisplayName("同一天连调 3 次返回同一个 questionId")
    void sameDayReturnsSameQuestion() throws Exception {
        String first = fetchDaily(tokenA).path("questionId").asText();
        String second = fetchDaily(tokenA).path("questionId").asText();
        String third = fetchDaily(tokenA).path("questionId").asText();

        System.out.println("[每日一题 x3] " + List.of(first, second, third));
        assertFalse(first.isBlank());
        assertEquals(first, second, "同一天第二次调用必须返回同一道题，不许重新生成");
        assertEquals(first, third, "同一天第三次调用同样必须一致");
        assertTrue(dailyRepository.findByUserIdAndDailyDate(userAId, LocalDate.now()).isPresent(),
                "(user_id, daily_date) 必须有且只有一行");
    }

    @Test
    @DisplayName("不同用户的每日一题不同")
    void differentUsersGetDifferentQuestion() throws Exception {
        String idA = fetchDaily(tokenA).path("questionId").asText();
        String idB = fetchDaily(tokenB).path("questionId").asText();

        System.out.println("[每日一题 A] " + idA);
        System.out.println("[每日一题 B] " + idB);
        // 模块不同必然不同题；模块相同时题也从池里取不同的一道（池为空时各自实时生成，也必然不同）
        assertFalse(idA.equals(idB), "两个用户的今日一题必须是不同的题");
    }

    @Test
    @DisplayName("返回结构与出题响应一致且不含答案")
    void responseShapeMatchesQuestionView() throws Exception {
        JsonNode data = fetchDaily(tokenA);

        assertTrue(data.has("questionId"), "必须有 questionId");
        assertTrue(data.has("moduleKey"));
        assertTrue(data.has("type"));
        assertTrue(data.has("prompt"));
        assertTrue(data.has("payload"));
        assertTrue(data.has("timeLimitSec"));
        assertTrue(!data.has("answer"), "答案绝不下发");
        assertTrue(!data.path("payload").has("value"), "payload 里也不能有答案");
        System.out.println("[每日一题响应] " + data);
    }

    @Test
    @DisplayName("每日一题的题在库里有答案，能被正常判题")
    void dailyQuestionCanBeGraded() throws Exception {
        JsonNode data = fetchDaily(tokenA);
        String questionId = data.path("questionId").asText();

        QuestionEntity entity = questionRepository.findById(questionId).orElseThrow();
        assertNotNull(entity.getAnswer(), "服务端必须留存答案");
        assertFalse(entity.getAnswer().isBlank());

        // 池题永久有效；实时兜底题当天也在有效期内，两种都必须能判
        boolean usable = entity.getExpiresAt() == null
                || entity.getExpiresAt().isAfter(java.time.LocalDateTime.now());
        assertTrue(usable, "当天拿到的题必须能作答");
        assertTrue(List.of("POOL", "GENERATED").contains(entity.getSource()),
                "每日一题要么取池题，要么池空时实时生成，来源只能是这两种");
        System.out.println("[每日一题来源] " + entity.getSource() + " expires_at=" + entity.getExpiresAt());
    }

    @Test
    @DisplayName("模块按 user_id + 日期轮转：同用户同日期稳定，换天会变")
    void moduleRotationIsStableForSameDay() {
        LocalDate day = LocalDate.of(2026, 11, 2);
        String first = dailyQuestionService.pickModule(42L, day);
        String second = dailyQuestionService.pickModule(42L, day);
        assertEquals(first, second, "同一用户同一日期必须选到同一个模块");

        // 连续 30 天里至少出现过 2 个不同模块，说明日期真的参与了轮转
        long distinct = java.util.stream.IntStream.range(0, 30)
                .mapToObj(i -> dailyQuestionService.pickModule(42L, day.plusDays(i)))
                .distinct()
                .count();
        System.out.println("[每日一题轮转] 30 天内出现 " + distinct + " 个不同模块");
        assertTrue(distinct >= 2, "不同日期应当会换模块");
    }

    @Test
    @DisplayName("未登录访问 /api/daily 返回 401")
    void requiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/daily")).andExpect(status().isUnauthorized());
    }
}
