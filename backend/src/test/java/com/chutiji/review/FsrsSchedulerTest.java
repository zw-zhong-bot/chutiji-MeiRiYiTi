package com.chutiji.review;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * FSRS-5 算法单元测试。期望值来自官方实现 ts-fsrs v4.7.1
 * （FSRSVersion = "FSRS-5.0"，enable_short_term=false / enable_fuzz=false）的实际输出，
 * 不是手算的近似值。
 */
class FsrsSchedulerTest {

    private final FsrsScheduler scheduler = new FsrsScheduler();
    private final LocalDateTime now = LocalDateTime.of(2026, 1, 1, 0, 0);

    // 官方默认 19 参数原值
    @Test
    @DisplayName("使用 FSRS-5 官方默认 19 个参数，一个都不许改")
    void usesOfficialDefaultParameters() {
        double[] expected = {
                0.40255, 1.18385, 3.173, 15.69105, 7.1949, 0.5345, 1.4604, 0.0046, 1.54575,
                0.1192, 1.01925, 1.9395, 0.11, 0.29605, 2.2698, 0.2315, 2.9898, 0.51655, 0.6621
        };
        assertEquals(19, FsrsScheduler.W.length, "必须是 19 个参数 w[0..18]");
        for (int i = 0; i < expected.length; i++) {
            assertEquals(expected[i], FsrsScheduler.W[i], 1e-12, "w[" + i + "] 与官方默认值不符");
        }
        assertEquals(0.9, FsrsScheduler.DESIRED_RETENTION, 1e-12, "desired retention 固定 0.9");
    }

    @ParameterizedTest(name = "rating={0} -> S0={1}, D0={2}")
    @CsvSource({
            "1, 0.402550, 7.194900",
            "2, 1.183850, 6.488305",
            "3, 3.173000, 5.282434",
            "4, 15.691050, 3.224502"
    })
    @DisplayName("新卡首次评分：S0 = w[G-1]，D0 = w[4] - e^(w[5]*(G-1)) + 1")
    void newCardMatchesFormula(int rating, double expectedStability, double expectedDifficulty) {
        FsrsResult result = scheduler.schedule(FsrsState.fresh(), rating, now);

        assertEquals(expectedStability, result.next().stability(), 1e-6,
                "S0(G=" + rating + ") 应等于 w[" + (rating - 1) + "]");
        assertEquals(expectedDifficulty, result.next().difficultyFsrs(), 1e-6,
                "D0(G=" + rating + ") 与公式不符");
        assertEquals(1, result.next().reps());
        assertEquals(0, result.next().lapses(), "新卡首次评分不产生 lapse");
        assertEquals(FsrsState.REVIEW, result.next().state());
    }

    @Test
    @DisplayName("新卡 D0 公式独立复核：与 w[4] - e^(w[5]*(G-1)) + 1 逐位一致")
    void initialDifficultyFollowsFormula() {
        for (int rating = 1; rating <= 4; rating++) {
            double expected = FsrsScheduler.W[4] - Math.exp(FsrsScheduler.W[5] * (rating - 1)) + 1;
            expected = Math.min(Math.max(expected, 1.0), 10.0);
            assertEquals(expected, FsrsScheduler.initDifficulty(rating), 1e-12);
        }
    }

    @Test
    @DisplayName("新卡首档间隔：Again/Hard/Good/Easy 严格单调")
    void newCardIntervalsAreMonotonic() {
        double again = scheduler.schedule(FsrsState.fresh(), FsrsScheduler.AGAIN, now).intervalDays();
        double hard = scheduler.schedule(FsrsState.fresh(), FsrsScheduler.HARD, now).intervalDays();
        double good = scheduler.schedule(FsrsState.fresh(), FsrsScheduler.GOOD, now).intervalDays();
        double easy = scheduler.schedule(FsrsState.fresh(), FsrsScheduler.EASY, now).intervalDays();

        System.out.printf("[新卡四档] Again=%.0f Hard=%.0f Good=%.0f Easy=%.0f%n", again, hard, good, easy);
        assertEquals(1.0, again, 1e-9);
        assertTrue(hard > again, "Hard(" + hard + ") 必须大于 Again(" + again + ")");
        assertTrue(good > hard, "Good(" + good + ") 必须大于 Hard(" + hard + ")");
        assertTrue(easy > good, "Easy(" + easy + ") 必须大于 Good(" + good + ")");
    }

    @Test
    @DisplayName("复习卡四档评分：间隔单调 Easy > Good > Hard > Again，且 S/D 与官方实现一致")
    void reviewCardFourRatingsMatchOfficial() {
        // 官方 ts-fsrs 输入：stability=10, difficulty=5, last_review=2025-12-22, review=2026-01-01
        FsrsState card = new FsrsState(10.0, 5.0, 5, 1, FsrsState.REVIEW);

        FsrsResult again = scheduler.schedule(card, FsrsScheduler.AGAIN, now, 10);
        FsrsResult hard = scheduler.schedule(card, FsrsScheduler.HARD, now, 10);
        FsrsResult good = scheduler.schedule(card, FsrsScheduler.GOOD, now, 10);
        FsrsResult easy = scheduler.schedule(card, FsrsScheduler.EASY, now, 10);

        System.out.printf("[复习卡四档] Again: S=%.5f D=%.5f 间隔=%.0f%n",
                again.next().stability(), again.next().difficultyFsrs(), again.intervalDays());
        System.out.printf("[复习卡四档] Hard : S=%.5f D=%.5f 间隔=%.0f%n",
                hard.next().stability(), hard.next().difficultyFsrs(), hard.intervalDays());
        System.out.printf("[复习卡四档] Good : S=%.5f D=%.5f 间隔=%.0f%n",
                good.next().stability(), good.next().difficultyFsrs(), good.intervalDays());
        System.out.printf("[复习卡四档] Easy : S=%.5f D=%.5f 间隔=%.0f%n",
                easy.next().stability(), easy.next().difficultyFsrs(), easy.intervalDays());

        // 稳定性与难度逐值对齐官方实现
        assertEquals(2.107696, again.next().stability(), 1e-5);
        assertEquals(6.607035, again.next().difficultyFsrs(), 1e-5);
        assertEquals(15.313912, hard.next().stability(), 1e-5);
        assertEquals(5.799434, hard.next().difficultyFsrs(), 1e-5);
        assertEquals(32.954264, good.next().stability(), 1e-5);
        assertEquals(4.991833, good.next().difficultyFsrs(), 1e-5);
        assertEquals(78.628658, easy.next().stability(), 1e-5);
        assertEquals(4.184232, easy.next().difficultyFsrs(), 1e-5);

        assertTrue(easy.intervalDays() > good.intervalDays(), "Easy 间隔应大于 Good");
        assertTrue(good.intervalDays() > hard.intervalDays(), "Good 间隔应大于 Hard");
        assertTrue(hard.intervalDays() > again.intervalDays(), "Hard 间隔应大于 Again");
    }

    @Test
    @DisplayName("连续答对 3 次，间隔递增")
    void consecutiveSuccessIncreasesInterval() {
        FsrsState state = FsrsState.fresh();
        double previousInterval = 0;
        double[] intervals = new double[4];
        String[] labels = {"首次 Good", "第 1 次复习 Good", "第 2 次复习 Good", "第 3 次复习 Good"};
        LocalDateTime time = now;

        for (int i = 0; i < 4; i++) {
            FsrsResult result = scheduler.schedule(state, FsrsScheduler.GOOD, time, previousInterval);
            intervals[i] = result.intervalDays();
            System.out.printf("[连续答对] %s: S=%.5f D=%.5f 间隔=%.0f 天%n",
                    labels[i], result.next().stability(), result.next().difficultyFsrs(), intervals[i]);
            state = result.next();
            previousInterval = intervals[i];
            time = time.plusDays((long) intervals[i]);
        }

        for (int i = 1; i < intervals.length; i++) {
            assertTrue(intervals[i] > intervals[i - 1],
                    labels[i] + " 的间隔 " + intervals[i] + " 必须大于上一次 " + intervals[i - 1]);
        }
        assertEquals(3.0, intervals[0], 1e-9, "首次 Good 的 S0 = w[2] = 3.173 → 3 天");
        assertEquals(11.0, intervals[1], 1e-9);
        assertEquals(35.0, intervals[2], 1e-9);
        assertEquals(101.0, intervals[3], 1e-9);
    }

    @Test
    @DisplayName("答错后 stability 下降、间隔回到次日、lapses 累加")
    void lapseDecreasesStabilityAndReturnsToNextDay() {
        FsrsState mature = new FsrsState(100.7483, 5.2542, 10, 0, FsrsState.REVIEW);

        FsrsResult lapse = scheduler.schedule(mature, FsrsScheduler.AGAIN, now, 101);

        System.out.printf("[答错] 复习前 S=%.4f D=%.4f → 复习后 S=%.4f D=%.4f 间隔=%.0f 天 lapses=%d%n",
                mature.stability(), mature.difficultyFsrs(),
                lapse.next().stability(), lapse.next().difficultyFsrs(), lapse.intervalDays(), lapse.next().lapses());

        assertTrue(lapse.next().stability() < mature.stability(),
                "答错后 stability 必须下降：" + lapse.next().stability() + " 应小于 " + mature.stability());
        assertEquals(5.942953, lapse.next().stability(), 1e-5, "遗忘稳定度与官方实现一致");
        assertEquals(6.777949, lapse.next().difficultyFsrs(), 1e-5);
        assertEquals(1.0, lapse.intervalDays(), 1e-9, "答错后 due_at 回到次日");
        assertEquals(mature.lapses() + 1, lapse.next().lapses(), "lapses 累加 1");
        assertEquals(mature.reps() + 1, lapse.next().reps());
    }

    @Test
    @DisplayName("lapses 连续答错逐次累加，stability 逐次下降")
    void lapsesAccumulate() {
        FsrsState state = new FsrsState(10.0, 5.0, 0, 0, FsrsState.REVIEW);
        double previousStability = state.stability();
        int previousLapses = state.lapses();
        LocalDateTime time = now;
        double elapsed = 10;

        for (int i = 1; i <= 3; i++) {
            FsrsResult result = scheduler.schedule(state, FsrsScheduler.AGAIN, time, elapsed);
            System.out.printf("[连续答错 #%d] S=%.6f D=%.6f 间隔=%.0f lapses=%d%n",
                    i, result.next().stability(), result.next().difficultyFsrs(),
                    result.intervalDays(), result.next().lapses());
            assertEquals(previousLapses + 1, result.next().lapses(), "lapses 应逐次 +1");
            assertTrue(result.next().stability() < previousStability, "每次答错 stability 都应继续下降");
            assertEquals(1.0, result.intervalDays(), 1e-9);
            previousLapses = result.next().lapses();
            previousStability = result.next().stability();
            elapsed = result.intervalDays();
            state = result.next();
            time = time.plusDays(1);
        }
        assertEquals(3, previousLapses);
    }

    @Test
    @DisplayName("间隔护栏：极端稳定度下仍 clamp 到 [1, 365]")
    void intervalClampedToOneTo365() {
        // 下界：稳定度极小的卡，公式会算出 0 天，必须被托到 1 天
        FsrsState tiny = new FsrsState(0.0001, 10.0, 1, 0, FsrsState.REVIEW);
        for (int rating = 1; rating <= 4; rating++) {
            double interval = scheduler.schedule(tiny, rating, now, 0).intervalDays();
            assertTrue(interval >= 1.0, "rating=" + rating + " 的间隔 " + interval + " 不得小于 1 天");
        }

        // 上界：稳定度 100000 天时公式会算出 ~100000 天，必须被压到 365 天
        FsrsState huge = new FsrsState(100000.0, 5.0, 20, 0, FsrsState.REVIEW);
        for (int rating = 1; rating <= 4; rating++) {
            double interval = scheduler.schedule(huge, rating, now, 10).intervalDays();
            System.out.printf("[护栏] S=100000, rating=%d → 间隔 %.0f 天%n", rating, interval);
            assertTrue(interval <= 365.0, "rating=" + rating + " 的间隔 " + interval + " 不得大于 365 天");
            assertTrue(interval >= 1.0, "rating=" + rating + " 的间隔 " + interval + " 不得小于 1 天");
        }

        // 连打 30 次 Easy，间隔也绝不允许越过 365
        FsrsState state = FsrsState.fresh();
        double elapsed = 0;
        for (int i = 0; i < 30; i++) {
            FsrsResult result = scheduler.schedule(state, FsrsScheduler.EASY, now, elapsed);
            assertTrue(result.intervalDays() <= 365.0 && result.intervalDays() >= 1.0,
                    "第 " + i + " 次 Easy 的间隔 " + result.intervalDays() + " 越界");
            state = result.next();
            elapsed = result.intervalDays();
        }
        assertEquals(365.0, scheduler.schedule(state, FsrsScheduler.EASY, now, 365).intervalDays(), 1e-9,
                "稳定度极大时 Easy 顶到 365 天上限");
    }

    @Test
    @DisplayName("可提取性 R 与公式一致，且随时间递减")
    void retrievabilityFollowsFormula() {
        double s = 10.0;
        double r0 = FsrsScheduler.retrievability(0, s);
        double r10 = FsrsScheduler.retrievability(10, s);
        assertEquals(1.0, r0, 1e-12, "t=0 时 R 必须是 1");
        assertEquals(Math.pow(1 + FsrsScheduler.FACTOR * 10 / s, FsrsScheduler.DECAY), r10, 1e-12);
        assertTrue(r10 < r0);
        assertEquals(0.9, FsrsScheduler.retrievability(s, s), 1e-9, "t=S 时 R 应等于 0.9（desired retention）");
    }

    @Test
    @DisplayName("FACTOR = 19/81、DECAY = -0.5（FSRS-4.5/5 固定常数）")
    void constantsMatchOfficial() {
        assertEquals(-0.5, FsrsScheduler.DECAY, 1e-12);
        assertEquals(19.0 / 81.0, FsrsScheduler.FACTOR, 1e-12);
        assertEquals(1.0, FsrsScheduler.intervalModifier(0.9), 1e-9, "retention=0.9 时间隔系数恰为 1");
    }

    @Test
    @DisplayName("rating 越界直接拒绝")
    void rejectsInvalidRating() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> scheduler.schedule(FsrsState.fresh(), 0, now));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> scheduler.schedule(FsrsState.fresh(), 5, now));
    }
}
