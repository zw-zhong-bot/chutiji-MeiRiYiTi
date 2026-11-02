package com.chutiji.review;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
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

import com.chutiji.question.AnswerRecordRepository;
import com.chutiji.question.QuestionRepository;
import com.chutiji.security.JwtService;
import com.chutiji.user.UserEntity;
import com.chutiji.user.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * GET /api/review/stats：按已知数据验算每个指标、last7Days 补零、用户隔离。
 *
 * <p>统计口径（设计文档 4.2 + 10.3 裁定 2）：
 * totalCards/dueCards/newCards/avgStability 来自 t_review_card；
 * totalReviews/totalLapses = sum(reps)/sum(lapses)；
 * last7Days 来自 t_review_log，correct 以 rating &gt; 1 判定。
 */
@SpringBootTest(properties = "app.question.cleanup-cron=-")
@AutoConfigureMockMvc
@Transactional
class ReviewStatsIntegrationTest {

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
    private String tokenB;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        userAId = saveUser("st_a_" + suffix);
        userBId = saveUser("st_b_" + suffix);
        tokenA = jwtService.issue(userAId, "st_a_" + suffix).token();
        tokenB = jwtService.issue(userBId, "st_b_" + suffix).token();
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

    /** 造一张卡；state=NEW 的卡视为"新卡"，否则算进 reviewCards。 */
    private ReviewCardEntity saveCard(Long userId, String moduleKey, String suffixKey,
                                      double stability, int reps, int lapses,
                                      String state, LocalDateTime dueAt, LocalDateTime lastReviewAt) {
        ReviewCardEntity card = new ReviewCardEntity();
        card.setUserId(userId);
        card.setModuleKey(moduleKey);
        card.setKnowledgeKey(moduleKey + ":stats-" + suffixKey);
        card.setDifficulty("{\"k\":\"" + suffixKey + "\"}");
        card.setStability(stability);
        card.setDifficultyFsrs(5.0);
        card.setReps(reps);
        card.setLapses(lapses);
        card.setState(state);
        card.setDueAt(dueAt);
        card.setLastReviewAt(lastReviewAt);
        return cardRepository.save(card);
    }

    private void saveLog(Long cardId, int rating, LocalDateTime reviewedAt) {
        ReviewLogEntity log = new ReviewLogEntity();
        log.setCardId(cardId);
        log.setRating(rating);
        log.setSBefore(1.0);
        log.setDBefore(5.0);
        log.setSAfter(2.0);
        log.setDAfter(5.0);
        log.setIntervalDays(1.0);
        logRepository.save(log);
        // reviewed_at 是 insertable=false（由 DDL DEFAULT 生成），要造历史日期必须显式 UPDATE。
        entityManager.flush();
        entityManager.createNativeQuery("UPDATE t_review_log SET reviewed_at = :ts WHERE id = :id")
                .setParameter("ts", reviewedAt)
                .setParameter("id", log.getId())
                .executeUpdate();
    }

    private JsonNode fetchStats(String token) throws Exception {
        String body = mockMvc.perform(get("/api/review/stats").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        System.out.println("[GET /api/review/stats] " + body);
        JsonNode root = objectMapper.readTree(body);
        assertEquals(0, root.path("code").asInt(), "包络 code 必须为 0");
        return root.path("data");
    }

    @Test
    @DisplayName("已知数据验算：卡片类、累计类、平均稳定度、按模块分布全部对得上")
    void computesKnownFixture() throws Exception {
        LocalDateTime now = LocalDateTime.now();

        // arithmetic：3 张卡，S = 10/20/30，reps = 4/2/0，lapses = 1/1/0，其中 1 张是 NEW
        saveCard(userAId, "arithmetic", "a1", 10.0, 4, 1, FsrsState.REVIEW, now.minusDays(1), now.minusDays(3));
        saveCard(userAId, "arithmetic", "a2", 20.0, 2, 1, FsrsState.REVIEW, now.plusDays(3), now);
        // NEW 卡在建卡时 due_at = 建卡时刻（立即可复习）。这里取 now 前 1 小时而不是恰好 now：
        // 列是 DATETIME(0)，恰好等于 now 会因秒级取整与断言时刻赛跑，导致 dueCards 在 2/3 间抖动。
        saveCard(userAId, "arithmetic", "a3", 30.0, 0, 0, FsrsState.NEW, now.minusHours(1), null);
        // sudoku：1 张卡，S = 40，reps = 3，lapses = 2，已到期
        saveCard(userAId, "sudoku", "s1", 40.0, 3, 2, FsrsState.REVIEW, now.minusDays(2), now.minusDays(5));

        entityManager.flush();
        entityManager.clear();

        JsonNode data = fetchStats(tokenA);

        assertEquals(4, data.path("totalCards").asInt(), "totalCards = 4 张");
        assertEquals(3, data.path("dueCards").asInt(),
                "dueCards：due_at <= now 的 3 张（a1、a3、s1）；a2 排在未来不算");
        assertEquals(1, data.path("newCards").asInt(), "newCards：state=NEW 的 1 张");
        assertEquals(3, data.path("reviewCards").asInt(), "reviewCards = totalCards - newCards = 3");
        assertEquals(9, data.path("totalReviews").asInt(), "totalReviews = 4+2+0+3 = 9");
        assertEquals(4, data.path("totalLapses").asInt(), "totalLapses = 1+1+0+2 = 4");
        assertEquals(25.0, data.path("avgStability").asDouble(), 1e-9, "(10+20+30+40)/4 = 25.0");

        JsonNode byModule = data.path("byModule");
        assertEquals(2, byModule.size(), "两个模块各一行");
        JsonNode arithmetic = byModule.get(0);
        assertEquals("arithmetic", arithmetic.path("moduleKey").asText());
        assertEquals(3, arithmetic.path("cards").asInt());
        assertEquals(6, arithmetic.path("reviews").asInt(), "arithmetic 的 reps 合计 4+2+0 = 6");
        assertEquals(2, arithmetic.path("lapses").asInt(), "arithmetic 的 lapses 合计 1+1+0 = 2");
        JsonNode sudoku = byModule.get(1);
        assertEquals("sudoku", sudoku.path("moduleKey").asText());
        assertEquals(1, sudoku.path("cards").asInt());
        assertEquals(3, sudoku.path("reviews").asInt());
        assertEquals(2, sudoku.path("lapses").asInt());

        assertEquals(7, data.path("last7Days").size(), "没有日志也必须有完整 7 天");
    }

    @Test
    @DisplayName("last7Days：只统计最近 7 个自然日，按日期升序，无数据的日期补 0 且不缺天")
    void last7DaysFillsGaps() throws Exception {
        LocalDateTime now = LocalDateTime.now();
        LocalDate today = now.toLocalDate();
        ReviewCardEntity card = saveCard(userAId, "arithmetic", "d1", 5.0, 0, 0,
                FsrsState.REVIEW, now, now);
        entityManager.flush();
        entityManager.clear();

        // 今天：3 条，2 条答对（rating>1）、1 条 Again
        saveLog(card.getId(), 3, today.atTime(9, 0));
        saveLog(card.getId(), 4, today.atTime(10, 0));
        saveLog(card.getId(), 1, today.atTime(11, 0));
        // 3 天前：2 条，1 对 1 错
        saveLog(card.getId(), 2, today.minusDays(3).atTime(8, 0));
        saveLog(card.getId(), 1, today.minusDays(3).atTime(9, 0));
        // 第 7 天前（窗口外，正好被排除）：1 条
        saveLog(card.getId(), 3, today.minusDays(7).atTime(8, 0));

        entityManager.flush();
        entityManager.clear();

        JsonNode days = fetchStats(tokenA).path("last7Days");
        assertEquals(7, days.size(), "必须正好 7 天，不能因为缺数据少几天");

        List<String> dates = new ArrayList<>();
        for (JsonNode day : days) {
            dates.add(day.path("date").asText());
        }
        List<String> expected = new ArrayList<>();
        for (int i = 6; i >= 0; i--) {
            expected.add(today.minusDays(i).toString());
        }
        assertEquals(expected, dates, "日期必须是最近 7 个自然日、升序、连续");

        // 今天：3 条，correct = 2
        JsonNode todayStat = days.get(6);
        assertEquals(today.toString(), todayStat.path("date").asText());
        assertEquals(3, todayStat.path("reviews").asInt());
        assertEquals(2, todayStat.path("correct").asInt(), "rating>1 记为答对：3、4 两条");

        // 3 天前在索引 3（7 天窗口里 from 最早 → 今天最后）
        JsonNode threeDaysAgo = days.get(3);
        assertEquals(today.minusDays(3).toString(), threeDaysAgo.path("date").asText());
        assertEquals(2, threeDaysAgo.path("reviews").asInt());
        assertEquals(1, threeDaysAgo.path("correct").asInt(), "rating=2 算对、rating=1 算错");

        // 中间没数据的天必须补 0，而不是被跳过
        JsonNode gapDay = days.get(5);
        assertEquals(today.minusDays(1).toString(), gapDay.path("date").asText());
        assertEquals(0, gapDay.path("reviews").asInt(), "前一天没有复习记录，必须补 0");
        assertEquals(0, gapDay.path("correct").asInt());

        int sum = 0;
        for (JsonNode day : days) {
            sum += day.path("reviews").asInt();
        }
        assertEquals(5, sum, "窗口内合计 5 条；第 7 天前那条必须被排除");
    }

    @Test
    @DisplayName("用户隔离：A 的统计不含 B 的卡片与日志")
    void statsAreIsolatedPerUser() throws Exception {
        LocalDateTime now = LocalDateTime.now();

        saveCard(userAId, "arithmetic", "i1", 10.0, 2, 0, FsrsState.REVIEW, now.minusDays(1), now.minusDays(2));
        saveCard(userAId, "sudoku", "i2", 20.0, 1, 1, FsrsState.REVIEW, now.minusDays(1), now.minusDays(3));

        ReviewCardEntity cardB = saveCard(userBId, "geometry", "i3", 100.0, 50, 40,
                FsrsState.REVIEW, now.minusDays(1), now.minusDays(2));
        saveLog(cardB.getId(), 1, now.toLocalDate().atTime(9, 0));
        saveLog(cardB.getId(), 4, now.toLocalDate().atTime(10, 0));

        entityManager.flush();
        entityManager.clear();

        JsonNode dataA = fetchStats(tokenA);
        assertEquals(2, dataA.path("totalCards").asInt(), "A 只有 2 张卡");
        assertEquals(3, dataA.path("totalReviews").asInt(), "A 的 totalReviews = 2+1");
        assertEquals(1, dataA.path("totalLapses").asInt());
        assertEquals(15.0, dataA.path("avgStability").asDouble(), 1e-9, "(10+20)/2，B 的 100 不得混进来");
        assertEquals(2, dataA.path("byModule").size(), "A 的两个模块");
        for (JsonNode module : dataA.path("byModule")) {
            assertFalse("geometry".equals(module.path("moduleKey").asText()), "不得出现 B 的模块");
        }
        assertEquals(0, dataA.path("last7Days").get(6).path("reviews").asInt(),
                "A 自己今天没有日志，B 的 2 条不得算进来");

        JsonNode dataB = fetchStats(tokenB);
        assertEquals(1, dataB.path("totalCards").asInt(), "B 只有 1 张卡");
        assertEquals(50, dataB.path("totalReviews").asInt());
        assertEquals(40, dataB.path("totalLapses").asInt());
        assertEquals(100.0, dataB.path("avgStability").asDouble(), 1e-9);
        assertEquals("geometry", dataB.path("byModule").get(0).path("moduleKey").asText());
        assertEquals(2, dataB.path("last7Days").get(6).path("reviews").asInt(), "B 今天 2 条");
        assertEquals(1, dataB.path("last7Days").get(6).path("correct").asInt(), "B 今天答对 1 条");
    }

    @Test
    @DisplayName("没有任何数据时返回零值，仍给出完整 7 天与空模块列表")
    void emptyUserGetsZeroedStats() throws Exception {
        JsonNode data = fetchStats(tokenA);

        assertEquals(0, data.path("totalCards").asInt());
        assertEquals(0, data.path("dueCards").asInt());
        assertEquals(0, data.path("newCards").asInt());
        assertEquals(0, data.path("reviewCards").asInt());
        assertEquals(0, data.path("totalReviews").asInt());
        assertEquals(0, data.path("totalLapses").asInt());
        assertEquals(0.0, data.path("avgStability").asDouble(), 1e-9, "0 张卡时平均稳定度按 0 处理，不得 NaN");
        assertEquals(0, data.path("byModule").size());
        assertEquals(7, data.path("last7Days").size(), "新用户也要有 7 天补零");
        for (JsonNode day : data.path("last7Days")) {
            assertEquals(0, day.path("reviews").asInt());
            assertEquals(0, day.path("correct").asInt());
        }
    }

    @Test
    @DisplayName("复习链路驱动的真实统计：答错建卡后 totalCards=1、totalReviews=1")
    void statsReflectRealReviewFlow() throws Exception {
        String body = objectMapper.writeValueAsString(java.util.Map.of(
                "moduleKey", "arithmetic",
                "difficulty", java.util.Map.of("maxOperand", 100, "operatorCount", 2)));
        String created = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/questions")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String questionId = objectMapper.readTree(created).path("data").path("questionId").asText();

        String gradeBody = objectMapper.writeValueAsString(java.util.Map.of("answer", "not-a-number", "durationMs", 1000));
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/questions/" + questionId + "/grade")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(gradeBody))
                .andExpect(status().isOk());

        entityManager.flush();
        entityManager.clear();

        JsonNode data = fetchStats(tokenA);
        assertTrue(data.path("totalCards").asInt() >= 1, "答错应至少建一张卡");
        assertEquals(data.path("totalCards").asInt(), data.path("totalReviews").asInt(),
                "每张卡 reps=1，所以 totalReviews 与卡片数一致");
        assertEquals(1, data.path("last7Days").get(6).path("reviews").asInt(), "今天应有 1 条复习记录");
        assertEquals(0, data.path("last7Days").get(6).path("correct").asInt(), "答错记 rating=1，不算 correct");
    }

    @Test
    @DisplayName("未登录访问 /api/review/stats 返回 401")
    void requiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/review/stats")).andExpect(status().isUnauthorized());
    }
}
