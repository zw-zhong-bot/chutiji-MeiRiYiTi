package com.chutiji.record;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

import com.chutiji.question.AnswerRecordEntity;
import com.chutiji.question.AnswerRecordRepository;
import com.chutiji.security.JwtService;
import com.chutiji.user.UserEntity;
import com.chutiji.user.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import com.fasterxml.jackson.databind.ObjectMapper;

/** GET /api/records：必须只返回当前登录用户的记录，且不含 submitted。 */
@SpringBootTest(properties = "app.question.cleanup-cron=-")
@AutoConfigureMockMvc
@Transactional
class RecordControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AnswerRecordRepository answerRecordRepository;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private ObjectMapper objectMapper;

    @PersistenceContext
    private EntityManager entityManager;

    private Long userAId;
    private Long userBId;
    private String tokenA;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        userAId = saveUser("rec_a_" + suffix);
        userBId = saveUser("rec_b_" + suffix);
        tokenA = jwtService.issue(userAId, "rec_a_" + suffix).token();

        // A 做 3 道题，B 做 2 道题
        saveRecord(userAId, "q_a_1", true, 1000);
        saveRecord(userAId, "q_a_2", false, 2000);
        saveRecord(userAId, "q_a_3", true, 3000);
        saveRecord(userBId, "q_b_1", true, 4000);
        saveRecord(userBId, "q_b_2", false, 5000);

        // created_at 由数据库 DEFAULT CURRENT_TIMESTAMP 生成，实体侧 insertable=false：
        // 必须 flush + clear 才会从库里读回真实值
        entityManager.flush();
        entityManager.clear();
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

    private void saveRecord(Long userId, String questionId, boolean correct, int durationMs) {
        AnswerRecordEntity record = new AnswerRecordEntity();
        record.setUserId(userId);
        record.setQuestionId(questionId);
        record.setModuleKey("arithmetic");
        record.setSubmitted("{\"answer\":\"SECRET_SHOULD_NOT_LEAK\"}");
        record.setCorrect(correct);
        record.setDurationMs(durationMs);
        answerRecordRepository.save(record);
    }

    @Test
    @DisplayName("只返回当前用户的记录，total 不含其他用户")
    void returnsOnlyCurrentUserRecords() throws Exception {
        String body = mockMvc.perform(get("/api/records").param("page", "0").param("size", "20")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode data = objectMapper.readTree(body).path("data");
        assertEquals(3, data.path("total").asInt(), "total 只能是 A 的 3 条");
        assertEquals(1, data.path("totalPages").asInt());
        assertEquals(0, data.path("page").asInt());
        assertEquals(20, data.path("size").asInt());

        List<String> questionIds = new java.util.ArrayList<>();
        data.path("items").forEach(item -> questionIds.add(item.path("questionId").asText()));
        assertEquals(List.of("q_a_1", "q_a_2", "q_a_3").size(), questionIds.size());
        assertTrue(questionIds.containsAll(List.of("q_a_1", "q_a_2", "q_a_3")));
        assertFalse(questionIds.contains("q_b_1"), "不得出现 B 的记录");
        assertFalse(questionIds.contains("q_b_2"), "不得出现 B 的记录");
        assertFalse(body.contains("SECRET_SHOULD_NOT_LEAK"), "响应不得包含 submitted 内容");
        assertFalse(body.contains("\"submitted\""), "响应不得包含 submitted 字段");
    }

    @Test
    @DisplayName("item 字段为白名单：id/questionId/moduleKey/correct/durationMs/createdAt")
    void itemHasWhitelistedFields() throws Exception {
        String body = mockMvc.perform(get("/api/records").header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode first = objectMapper.readTree(body).path("data").path("items").get(0);
        assertTrue(first.hasNonNull("id"));
        assertTrue(first.hasNonNull("questionId"));
        assertTrue(first.hasNonNull("moduleKey"));
        assertTrue(first.has("correct"));
        assertTrue(first.has("durationMs"));
        assertTrue(first.hasNonNull("createdAt"));
        assertFalse(first.has("submitted"));
        assertFalse(first.has("userId"), "不应暴露 userId 之外的内部字段");
    }

    @Test
    @DisplayName("size=1000 时按 100 处理，不报错")
    void clampsSizeToOneHundred() throws Exception {
        String body = mockMvc.perform(get("/api/records").param("size", "1000")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode data = objectMapper.readTree(body).path("data");
        assertEquals(100, data.path("size").asInt(), "size 上限 100");
        assertTrue(data.path("items").size() <= 100);
    }

    @Test
    @DisplayName("未登录访问 /api/records 返回 401")
    void requiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/records")).andExpect(status().isUnauthorized());
    }
}
