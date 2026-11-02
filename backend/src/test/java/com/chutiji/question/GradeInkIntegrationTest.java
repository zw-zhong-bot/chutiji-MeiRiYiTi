package com.chutiji.question;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

import com.chutiji.security.JwtService;
import com.chutiji.user.UserEntity;
import com.chutiji.user.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * 笔迹随作答提交（设计文档 7.4）：ink 原样透传存进 t_answer_record.submitted，
 * 只存不识别——判题结果不受它影响，不下发，也没有任何形状校验。
 */
@SpringBootTest(properties = "app.question.cleanup-cron=-")
@AutoConfigureMockMvc
@Transactional
class GradeInkIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private QuestionRepository questionRepository;

    @Autowired
    private AnswerRecordRepository answerRecordRepository;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private ObjectMapper objectMapper;

    @PersistenceContext
    private EntityManager entityManager;

    private Long userId;
    private String token;

    /** 几何题：FIGURE 是唯一有笔迹层的题型。 */
    private static final Map<String, Object> GEOMETRY_DIFFICULTY = new LinkedHashMap<>();

    static {
        GEOMETRY_DIFFICULTY.put("shapeComplexity", "BASIC");
        GEOMETRY_DIFFICULTY.put("needAuxiliary", false);
    }

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        UserEntity user = new UserEntity();
        user.setUsername("ink_" + suffix);
        user.setPasswordHash("x");
        user.setNickname("ink_" + suffix);
        user.setRole("USER");
        user.setEnabled(Boolean.TRUE);
        userId = userRepository.save(user).getId();
        token = jwtService.issue(userId, "ink_" + suffix).token();
    }

    private String createQuestion() throws Exception {
        String body = objectMapper.writeValueAsString(
                Map.of("moduleKey", "geometry", "difficulty", GEOMETRY_DIFFICULTY));
        String response = mockMvc.perform(post("/api/questions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode data = objectMapper.readTree(response).path("data");
        assertFalse(response.contains("\"answer\""), "出题响应仍绝不下发 answer");
        return data.path("questionId").asText();
    }

    private JsonNode grade(String questionId, String answer, Object ink) throws Exception {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("answer", answer);
        request.put("durationMs", 4200);
        if (ink != null) {
            request.put("ink", ink);
        }
        String response = mockMvc.perform(post("/api/questions/" + questionId + "/grade")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).path("data");
    }

    private AnswerRecordEntity latestRecord() {
        entityManager.flush();
        entityManager.clear();
        List<AnswerRecordEntity> records = answerRecordRepository.findAll().stream()
                .filter(record -> userId.equals(record.getUserId()))
                .toList();
        return records.get(records.size() - 1);
    }

    @Test
    @DisplayName("带 ink 提交：submitted 里出现 ink，点数组原样保存")
    void inkIsPersistedVerbatim() throws Exception {
        String questionId = createQuestion();

        List<Map<String, Object>> ink = List.of(
                Map.of("width", 3,
                        "points", List.of(
                                Map.of("x", 10.5, "y", 20.25, "pressure", 0.5, "t", 120),
                                Map.of("x", 30.0, "y", 40.75, "pressure", 0.62, "t", 140))),
                Map.of("width", 6,
                        "points", List.of(Map.of("x", 5.0, "y", 6.0, "pressure", 0.5, "t", 300))));

        grade(questionId, "answer-text", ink);
        AnswerRecordEntity record = latestRecord();

        System.out.println("[ink 落库] submitted = " + record.getSubmitted());

        JsonNode submitted = objectMapper.readTree(record.getSubmitted());
        assertEquals("answer-text", submitted.path("answer").asText(), "answer 字段必须仍按老形状存");
        assertTrue(submitted.has("ink"), "submitted 里必须有 ink 字段");

        JsonNode storedInk = submitted.path("ink");
        assertEquals(2, storedInk.size(), "两笔都要存下来");
        assertEquals(3, storedInk.get(0).path("width").asInt());
        JsonNode points = storedInk.get(0).path("points");
        assertEquals(2, points.size());
        assertEquals(10.5, points.get(0).path("x").asDouble(), 1e-9, "坐标必须原样保存，不四舍五入");
        assertEquals(20.25, points.get(0).path("y").asDouble(), 1e-9);
        assertEquals(0.62, points.get(1).path("pressure").asDouble(), 1e-9);
        assertEquals(140, points.get(1).path("t").asInt());
        assertEquals(6, storedInk.get(1).path("width").asInt());
    }

    @Test
    @DisplayName("不带 ink 提交：submitted 仍是 {\"answer\":...}，不多出空 ink 键")
    void omittedInkLeavesShapeUnchanged() throws Exception {
        String questionId = createQuestion();
        grade(questionId, "42", null);

        AnswerRecordEntity record = latestRecord();
        System.out.println("[无 ink 落库] submitted = " + record.getSubmitted());

        JsonNode submitted = objectMapper.readTree(record.getSubmitted());
        assertEquals("42", submitted.path("answer").asText());
        assertFalse(submitted.has("ink"), "没有笔迹时不得写入 ink 键，保持既有记录形状");
    }

    @Test
    @DisplayName("空笔画/怪形状的 ink 也照存不误：服务端不做任何识别或校验")
    void inkIsNotValidated() throws Exception {
        String questionId = createQuestion();

        Map<String, Object> weirdInk = new LinkedHashMap<>();
        weirdInk.put("anything", "goes");
        weirdInk.put("strokes", List.of());

        grade(questionId, "x", weirdInk);
        AnswerRecordEntity record = latestRecord();

        JsonNode storedInk = objectMapper.readTree(record.getSubmitted()).path("ink");
        assertEquals("goes", storedInk.path("anything").asText(),
                "ink 是留档数据，服务端不得按形状拒绝或改写");
        assertEquals(0, storedInk.path("strokes").size());
    }

    @Test
    @DisplayName("带 ink 不改变判题结果：ink 只存不参与判分，响应里也没有 ink")
    void inkDoesNotAffectGrading() throws Exception {
        String withoutInkQuestion = createQuestion();
        JsonNode withoutInk = grade(withoutInkQuestion, "not-a-number", null);

        String withInkQuestion = createQuestion();
        JsonNode withInk = grade(withInkQuestion, "not-a-number",
                List.of(Map.of("width", 3, "points", List.of(Map.of("x", 1.0, "y", 2.0, "pressure", 0.5, "t", 1)))));

        assertEquals(withoutInk.path("correct").asBoolean(), withInk.path("correct").asBoolean(),
                "同一个错答案，带不带 ink 判题结果必须一致");
        assertFalse(withInk.path("correct").asBoolean(), "错答案仍判错");
        assertFalse(withInk.has("ink"), "判题响应不得回显笔迹");
    }
}
