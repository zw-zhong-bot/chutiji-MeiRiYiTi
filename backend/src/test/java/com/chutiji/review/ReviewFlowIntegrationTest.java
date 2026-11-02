package com.chutiji.review;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
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

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/** 复习链路集成测试：答错建卡、答对不建卡、到期列表按用户隔离、复习题沿用卡片难度。 */
@SpringBootTest(properties = "app.question.cleanup-cron=-")
@AutoConfigureMockMvc
@Transactional
class ReviewFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private QuestionRepository questionRepository;

    @Autowired
    private ReviewCardRepository cardRepository;

    @Autowired
    private ReviewLogRepository logRepository;

    @Autowired
    private com.chutiji.question.AnswerRecordRepository answerRecordRepository;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private ObjectMapper objectMapper;

    @PersistenceContext
    private EntityManager entityManager;

    private Long userAId;
    private Long userBId;
    private String tokenA;
    private String tokenB;

    /** 口算这一档难度：用户 A 在这档做错。 */
    private static final Map<String, Object> DIFFICULTY = LinkedHashMap.newLinkedHashMap(4);

    static {
        DIFFICULTY.put("maxOperand", 100);
        DIFFICULTY.put("operatorCount", 2);
        DIFFICULTY.put("allowParentheses", false);
        DIFFICULTY.put("allowDivision", true);
    }

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        userAId = saveUser("rv_a_" + suffix);
        userBId = saveUser("rv_b_" + suffix);
        tokenA = jwtService.issue(userAId, "rv_a_" + suffix).token();
        tokenB = jwtService.issue(userBId, "rv_b_" + suffix).token();
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

    private String createQuestion(String token, Map<String, Object> difficulty) throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("moduleKey", "arithmetic", "difficulty", difficulty));
        String response = mockMvc.perform(post("/api/questions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).path("data").path("questionId").asText();
    }

    private String storedAnswer(String questionId) {
        QuestionEntity entity = questionRepository.findById(questionId).orElseThrow();
        try {
            return objectMapper.readTree(entity.getAnswer()).path("value").asText();
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private JsonNode submitAnswer(String token, String questionId, String answer, int durationMs) throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("answer", answer, "durationMs", durationMs));
        String response = mockMvc.perform(post("/api/questions/" + questionId + "/grade")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).path("data");
    }

    @Test
    @DisplayName("用户 A 答错一道口算题 → t_review_card 多一行，state 与 FSRS 初值正确")
    void wrongAnswerCreatesCard() throws Exception {
        String questionId = createQuestion(tokenA, DIFFICULTY);

        JsonNode result = submitAnswer(tokenA, questionId, "not-a-number", 3000);
        assertFalse(result.path("correct").asBoolean(), "提交非法答案必须判错");

        entityManager.flush();
        entityManager.clear();

        List<ReviewCardEntity> cards = cardRepository.findByUserIdOrderByDueAtAsc(userAId);
        assertEquals(1, cards.size(), "答错必须建一张复习卡");

        ReviewCardEntity card = cards.get(0);
        System.out.printf("[答错建卡] cardId=%d module=%s state=%s S=%.5f D=%.5f reps=%d lapses=%d due=%s%n",
                card.getId(), card.getModuleKey(), card.getState(), card.getStability(),
                card.getDifficultyFsrs(), card.getReps(), card.getLapses(), card.getDueAt());

        assertEquals("arithmetic", card.getModuleKey());
        assertEquals(FsrsState.REVIEW, card.getState());
        assertEquals(1, card.getReps(), "首次评分 reps=1");
        assertEquals(0, card.getLapses(), "新卡首次不记 lapse");
        assertEquals(0.40255, card.getStability(), 1e-5, "Again 的 S0 = w[0]");
        assertEquals(7.1949, card.getDifficultyFsrs(), 1e-5, "Again 的 D0 = w[4]");
        assertTrue(card.getDueAt().isAfter(LocalDateTime.now()), "答错后 due_at 排到次日之后");
        assertTrue(card.getDueAt().isBefore(LocalDateTime.now().plusDays(2)), "答错后 due_at 必须回到次日");
        assertEquals(KnowledgeKey.of("arithmetic", DIFFICULTY), card.getKnowledgeKey(), "knowledge_key 必须由模块 + 难度配置生成");

        List<ReviewLogEntity> logs = logRepository.findByCardIdOrderByReviewedAtAsc(card.getId());
        assertEquals(1, logs.size(), "每次评分必须写一条 log");
        assertEquals(1, logs.get(0).getRating(), "答错记 Again");
        assertEquals(1.0, logs.get(0).getIntervalDays(), 1e-9);
    }

    @Test
    @DisplayName("用户 B 答对同一档难度 → 不建卡")
    void correctAnswerDoesNotCreateCard() throws Exception {
        String questionId = createQuestion(tokenB, DIFFICULTY);
        String answer = storedAnswer(questionId);

        JsonNode result = submitAnswer(tokenB, questionId, answer, 5000);
        assertTrue(result.path("correct").asBoolean(), "标准答案必须判对");

        entityManager.flush();
        entityManager.clear();

        assertTrue(cardRepository.findByUserIdOrderByDueAtAsc(userBId).isEmpty(),
                "没做错过就不进复习队列：答对且无卡时不得建卡");
        assertTrue(cardRepository.findByUserIdOrderByDueAtAsc(userAId).isEmpty(),
                "其他用户同样不该被牵连建卡");
    }

    @Test
    @DisplayName("答对但已有卡 → 更新同一张卡，不新建")
    void correctAnswerUpdatesExistingCard() throws Exception {
        String wrongQuestion = createQuestion(tokenA, DIFFICULTY);
        submitAnswer(tokenA, wrongQuestion, "not-a-number", 3000);
        entityManager.flush();
        entityManager.clear();

        ReviewCardEntity before = cardRepository.findByUserIdOrderByDueAtAsc(userAId).get(0);
        double stabilityBefore = before.getStability();

        // 让这张卡真的"到期"：把上次复习时间退到 3 天前。
        // 同日重答（elapsed = 0）在官方 long-term 调度下不改变稳定度，只有经过时间才会。
        before.setLastReviewAt(LocalDateTime.now().minusDays(3));
        before.setDueAt(LocalDateTime.now().minusDays(1));
        cardRepository.save(before);
        entityManager.flush();
        entityManager.clear();

        String correctQuestion = createQuestion(tokenA, DIFFICULTY);
        String answer = storedAnswer(correctQuestion);
        submitAnswer(tokenA, correctQuestion, answer, 1000);
        entityManager.flush();
        entityManager.clear();

        List<ReviewCardEntity> cards = cardRepository.findByUserIdOrderByDueAtAsc(userAId);
        assertEquals(1, cards.size(), "同一档难度只能有一张卡");
        ReviewCardEntity after = cards.get(0);
        System.out.printf("[答对更新] S %.5f → %.5f, reps=%d, due=%s%n",
                stabilityBefore, after.getStability(), after.getReps(), after.getDueAt());
        assertEquals(2, after.getReps(), "第二次评分 reps=2");
        assertTrue(after.getStability() > stabilityBefore,
                "到期后答对，稳定度应上升：" + stabilityBefore + " → " + after.getStability());
        assertEquals(2, logRepository.findByCardIdOrderByReviewedAtAsc(after.getId()).size());
    }

    @Test
    @DisplayName("用户 A 查 /api/review/due 只看到自己的卡")
    void dueListIsolatedPerUser() throws Exception {
        String questionA = createQuestion(tokenA, DIFFICULTY);
        submitAnswer(tokenA, questionA, "not-a-number", 3000);
        String questionB = createQuestion(tokenB, DIFFICULTY);
        submitAnswer(tokenB, questionB, "not-a-number", 3000);
        entityManager.flush();
        entityManager.clear();

        // 把 A 的卡挪到过去，构造"到期"
        ReviewCardEntity cardA = cardRepository.findByUserIdOrderByDueAtAsc(userAId).get(0);
        cardA.setDueAt(LocalDateTime.now().minusDays(2));
        cardRepository.save(cardA);
        entityManager.flush();
        entityManager.clear();

        String bodyA = mockMvc.perform(get("/api/review/due").header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String bodyB = mockMvc.perform(get("/api/review/due").header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        System.out.println("[GET /api/review/due] A -> " + bodyA);
        System.out.println("[GET /api/review/due] B -> " + bodyB);

        JsonNode dataA = objectMapper.readTree(bodyA).path("data");
        JsonNode dataB = objectMapper.readTree(bodyB).path("data");

        assertEquals(1, dataA.size(), "A 只该看到自己到期的 1 张卡");
        assertEquals(0, dataB.size(), "B 的卡还没到期，且不得看到 A 的卡");
        assertEquals(cardA.getId().longValue(), dataA.get(0).path("cardId").asLong());
        assertFalse(bodyA.contains("rv_b_"), "响应不得泄露其他用户信息");
    }

    @Test
    @DisplayName("POST /api/review/{cardId}/start 用卡片存的难度配置生成同类新题")
    void startReviewUsesCardDifficulty() throws Exception {
        String questionId = createQuestion(tokenA, DIFFICULTY);
        submitAnswer(tokenA, questionId, "not-a-number", 3000);
        entityManager.flush();
        entityManager.clear();

        ReviewCardEntity card = cardRepository.findByUserIdOrderByDueAtAsc(userAId).get(0);

        String body = mockMvc.perform(post("/api/review/" + card.getId() + "/start")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        System.out.println("[POST /api/review/" + card.getId() + "/start] " + body);

        JsonNode data = objectMapper.readTree(body).path("data");
        String newQuestionId = data.path("questionId").asText();
        assertNotNull(newQuestionId);
        assertFalse(newQuestionId.isBlank());

        entityManager.flush();
        entityManager.clear();

        QuestionEntity generated = questionRepository.findById(newQuestionId).orElseThrow();
        Map<String, Object> cardDifficulty = objectMapper.readValue(card.getDifficulty(),
                new com.fasterxml.jackson.core.type.TypeReference<LinkedHashMap<String, Object>>() {
                });
        Map<String, Object> generatedDifficulty = objectMapper.readValue(generated.getDifficulty(),
                new com.fasterxml.jackson.core.type.TypeReference<LinkedHashMap<String, Object>>() {
                });

        System.out.println("[难度比对] 卡片 " + card.getDifficulty());
        System.out.println("[难度比对] 新题 " + generated.getDifficulty());
        assertEquals(cardDifficulty, generatedDifficulty, "复习题难度必须与卡片完全一致，不许模块内部再随机");
        assertEquals(card.getModuleKey(), generated.getModuleKey());
        assertEquals(KnowledgeKey.of(generated.getModuleKey(), generatedDifficulty),
                card.getKnowledgeKey(), "生成的题必须落在同一个知识点上");
    }

    @Test
    @DisplayName("GET /api/review/cards 返回全部卡（含未到期）")
    void cardsListsAllIncludingNotDue() throws Exception {
        String questionId = createQuestion(tokenA, DIFFICULTY);
        submitAnswer(tokenA, questionId, "not-a-number", 3000);
        entityManager.flush();
        entityManager.clear();

        String body = mockMvc.perform(get("/api/review/cards").header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        System.out.println("[GET /api/review/cards] " + body);

        JsonNode data = objectMapper.readTree(body).path("data");
        assertEquals(1, data.size(), "未到期的卡也必须出现在全量列表里");
        assertFalse(data.get(0).path("due").asBoolean(), "刚建好的卡还没到期");
        assertFalse(body.contains("userId"), "不得暴露 userId");
    }

    @Test
    @DisplayName("答对时按该档历史用时映射 rating：秒答记 Easy、超时记 Hard")
    void correctAnswerRatingUsesDurationPercentiles() throws Exception {
        // 先在这档做错一次，建卡
        String wrongQuestion = createQuestion(tokenA, DIFFICULTY);
        submitAnswer(tokenA, wrongQuestion, "not-a-number", 3000);
        entityManager.flush();
        entityManager.clear();
        ReviewCardEntity card = cardRepository.findByUserIdOrderByDueAtAsc(userAId).get(0);

        // 造 10 条同一档难度的历史作答用时（100..1000ms），P25≈325、P75≈775
        for (int i = 1; i <= 10; i++) {
            String id = createQuestion(tokenA, DIFFICULTY);
            QuestionEntity entity = questionRepository.findById(id).orElseThrow();
            com.chutiji.question.AnswerRecordEntity record = new com.chutiji.question.AnswerRecordEntity();
            record.setUserId(userAId);
            record.setQuestionId(entity.getId());
            record.setModuleKey(entity.getModuleKey());
            record.setSubmitted("{\"answer\":\"x\"}");
            record.setCorrect(Boolean.TRUE);
            record.setDurationMs(i * 100);
            answerRecordRepository.save(record);
        }
        entityManager.flush();
        entityManager.clear();

        // 秒答（50ms < P25）→ Easy
        card.setLastReviewAt(LocalDateTime.now().minusDays(5));
        cardRepository.save(card);
        entityManager.flush();
        entityManager.clear();
        String fastQuestion = createQuestion(tokenA, DIFFICULTY);
        submitAnswer(tokenA, fastQuestion, storedAnswer(fastQuestion), 50);
        entityManager.flush();
        entityManager.clear();

        List<ReviewLogEntity> logs = logRepository.findByCardIdOrderByReviewedAtAsc(card.getId());
        assertEquals(2, logs.size());
        System.out.printf("[用时映射] 50ms → rating=%d（期望 Easy=4）, 间隔=%.0f%n",
                logs.get(1).getRating(), logs.get(1).getIntervalDays());
        assertEquals(FsrsScheduler.EASY, logs.get(1).getRating(), "远低于 P25 的用时必须记 Easy");

        // 超时（5000ms > P75）→ Hard
        ReviewCardEntity again = cardRepository.findById(card.getId()).orElseThrow();
        again.setLastReviewAt(LocalDateTime.now().minusDays(5));
        cardRepository.save(again);
        entityManager.flush();
        entityManager.clear();
        String slowQuestion = createQuestion(tokenA, DIFFICULTY);
        submitAnswer(tokenA, slowQuestion, storedAnswer(slowQuestion), 5000);
        entityManager.flush();
        entityManager.clear();

        List<ReviewLogEntity> logs2 = logRepository.findByCardIdOrderByReviewedAtAsc(card.getId());
        assertEquals(3, logs2.size());
        System.out.printf("[用时映射] 5000ms → rating=%d（期望 Hard=2）, 间隔=%.0f%n",
                logs2.get(2).getRating(), logs2.get(2).getIntervalDays());
        assertEquals(FsrsScheduler.HARD, logs2.get(2).getRating(), "远高于 P75 的用时必须记 Hard");
    }

    @Test
    @DisplayName("未登录访问复习端点返回 401")
    void requiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/review/due")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/review/cards")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/review/1/start")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("复习卡不存在或不属于当前用户时 start 报错")
    void startRejectsForeignCard() throws Exception {
        String questionA = createQuestion(tokenA, DIFFICULTY);
        submitAnswer(tokenA, questionA, "not-a-number", 3000);
        entityManager.flush();
        entityManager.clear();
        ReviewCardEntity cardA = cardRepository.findByUserIdOrderByDueAtAsc(userAId).get(0);

        String body = mockMvc.perform(post("/api/review/" + cardA.getId() + "/start")
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertEquals(2010, objectMapper.readTree(body).path("code").asInt(), "不得用别人的卡复习");
    }

    @Test
    @DisplayName("同一档难度重复答错只有一张卡，reps/lapses 累加")
    void repeatedWrongAnswersKeepOneCard() throws Exception {
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            ids.add(createQuestion(tokenA, DIFFICULTY));
        }
        for (String id : ids) {
            submitAnswer(tokenA, id, "not-a-number", 3000);
        }
        entityManager.flush();
        entityManager.clear();

        List<ReviewCardEntity> cards = cardRepository.findByUserIdOrderByDueAtAsc(userAId);
        assertEquals(1, cards.size(), "同一档难度不得分裂成多张卡");
        ReviewCardEntity card = cards.get(0);
        System.out.printf("[重复答错] reps=%d lapses=%d S=%.6f%n", card.getReps(), card.getLapses(), card.getStability());
        assertEquals(3, card.getReps());
        assertEquals(2, card.getLapses(), "首次建卡不记 lapse，之后每次答错 +1");
        assertEquals(3, logRepository.findByCardIdOrderByReviewedAtAsc(card.getId()).size());
    }
}
