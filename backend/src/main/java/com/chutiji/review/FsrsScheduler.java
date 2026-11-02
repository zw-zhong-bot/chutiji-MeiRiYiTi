package com.chutiji.review;

import java.time.LocalDateTime;

/**
 * FSRS-5 调度器。参数固定为官方默认 19 个（w[0..18]），不做训练。
 *
 * <p>参数来源：open-spaced-repetition/ts-fsrs v4.7.1 的 {@code default_w}
 * （该版本 {@code FSRSVersion = "FSRS-5.0"}），亦见 awesome-fsrs wiki "The Algorithm # FSRS-4.5/5"。
 * 与 py-fsrs / rs-fsrs 的 FSRS-5 默认值一致。
 *
 * <p>调度语义对齐 ts-fsrs v4.7.1 的 long-term scheduler（{@code enable_short_term = false}），
 * 即"按天粒度、不含学习步"的形态；唯一差异是 Again 的间隔（见 {@link #AGAIN_INTERVAL_DAYS}）。
 */
public class FsrsScheduler {

    /** FSRS-5 官方默认参数 w[0..18]，一个数都不许改。 */
    static final double[] W = {
            0.40255, 1.18385, 3.173, 15.69105, 7.1949, 0.5345, 1.4604, 0.0046, 1.54575,
            0.1192, 1.01925, 1.9395, 0.11, 0.29605, 2.2698, 0.2315, 2.9898, 0.51655, 0.6621
    };

    /** 遗忘曲线指数（FSRS-4.5/5 固定 -0.5）。 */
    static final double DECAY = -0.5;
    /** FACTOR = 0.9^(1/DECAY) - 1 = 19/81。 */
    static final double FACTOR = 19.0 / 81.0;
    /** 目标记忆率 90%（设计文档 10.3 裁定 2.2）。 */
    static final double DESIRED_RETENTION = 0.9;
    static final double S_MIN = 0.01;
    static final double S_MAX = 36500.0;
    static final double DEFAULT_DIFFICULTY = 5.0;
    static final double MIN_INTERVAL_DAYS = 1.0;
    static final double MAX_INTERVAL_DAYS = 365.0;

    /**
     * Again 的间隔（天）。
     *
     * <p>官方默认调度器在 Review 卡答错时进入 Relearning，首个 relearning step 是 10 分钟；
     * 本项目时间粒度是天，又被"最短 1 天"护栏托底，因此 Again 一律排到次日
     * （设计文档 10.3 裁定 2.6 第 2 条：答错后 due_at 回到次日）。
     */
    static final double AGAIN_INTERVAL_DAYS = 1.0;

    public static final int AGAIN = 1;
    public static final int HARD = 2;
    public static final int GOOD = 3;
    public static final int EASY = 4;

    /** 间隔系数：desired retention = 0.9 时恰为 1。 */
    private static final double INTERVAL_MODIFIER = intervalModifier(DESIRED_RETENTION);

    /**
     * 按间隔公式算出的"稳定度 → 天数"系数：
     * I = S / FACTOR * (R^(1/DECAY) - 1)
     */
    static double intervalModifier(double retention) {
        return (Math.pow(retention, 1.0 / DECAY) - 1.0) / FACTOR;
    }

    /** 若调用方没有历史复习时间，按 t=0 处理（等价于"刚复习过"，稳定度不变）。 */
    public FsrsResult schedule(FsrsState current, int rating, LocalDateTime reviewTime) {
        return schedule(current, rating, reviewTime, 0.0);
    }

    /**
     * @param elapsedDays 距上次复习过去的天数；新卡传 0
     */
    public FsrsResult schedule(FsrsState current, int rating, LocalDateTime reviewTime, double elapsedDays) {
        if (rating < AGAIN || rating > EASY) {
            throw new IllegalArgumentException("rating 必须是 1..4，实际 " + rating);
        }
        FsrsState state = current == null ? FsrsState.fresh() : current;
        double elapsed = Math.max(0.0, elapsedDays);

        boolean newCard = state.isNew();
        double s = newCard ? initStability(rating) : nextStability(state.stability(), state.difficultyFsrs(), elapsed, rating);
        double d = newCard ? initDifficulty(rating) : nextDifficulty(state.difficultyFsrs(), rating);
        int reps = state.reps() + 1;
        int lapses = state.lapses() + (!newCard && rating == AGAIN ? 1 : 0);

        double interval = intervalFor(state, newCard, elapsed, rating);
        return new FsrsResult(new FsrsState(s, d, reps, lapses, FsrsState.REVIEW), interval);
    }

    // ---------------------------------------------------------------- 间隔

    /**
     * 四档评分一次性算完后按官方规则拉开档位：Hard &gt; Again、Good &gt; Hard、Easy &gt; Good，
     * 保证同一张卡四档评分的间隔严格单调。
     */
    private double intervalFor(FsrsState state, boolean newCard, double elapsed, int rating) {
        double again = AGAIN_INTERVAL_DAYS;
        double hard = Math.max(rawInterval(stabilityAt(state, newCard, elapsed, HARD)), again + 1);
        double good = Math.max(rawInterval(stabilityAt(state, newCard, elapsed, GOOD)), hard + 1);
        double easy = Math.max(rawInterval(stabilityAt(state, newCard, elapsed, EASY)), good + 1);
        double picked = switch (rating) {
            case AGAIN -> again;
            case HARD -> hard;
            case GOOD -> good;
            default -> easy;
        };
        return clampInterval(picked);
    }

    private double stabilityAt(FsrsState state, boolean newCard, double elapsed, int rating) {
        return newCard
                ? initStability(rating)
                : nextStability(state.stability(), state.difficultyFsrs(), elapsed, rating);
    }

    private double rawInterval(double stability) {
        return Math.round(stability * INTERVAL_MODIFIER);
    }

    static double clampInterval(double days) {
        return Math.min(Math.max(days, MIN_INTERVAL_DAYS), MAX_INTERVAL_DAYS);
    }

    // ---------------------------------------------------------------- 公式

    /** S0(G) = w[G-1]，下限 0.1。 */
    static double initStability(int rating) {
        return Math.max(W[rating - 1], 0.1);
    }

    /** D0(G) = w[4] - e^(w[5] * (G-1)) + 1，clamp 到 [1, 10]。 */
    static double initDifficulty(int rating) {
        return constrainDifficulty(W[4] - Math.exp(W[5] * (rating - 1)) + 1);
    }

    /** D' = w[7] * D0(4) + (1 - w[7]) * (D + deltaD * (10 - D) / 9)，deltaD = -w[6] * (G - 3)。 */
    static double nextDifficulty(double difficulty, int rating) {
        double delta = -W[6] * (rating - 3);
        double linearDamped = difficulty + delta * (10 - difficulty) / 9.0;
        return constrainDifficulty(meanReversion(initDifficulty(EASY), linearDamped));
    }

    private static double meanReversion(double init, double current) {
        return W[7] * init + (1 - W[7]) * current;
    }

    static double constrainDifficulty(double difficulty) {
        return Math.min(Math.max(difficulty, 1.0), 10.0);
    }

    /** R = (1 + FACTOR * t / S)^DECAY。 */
    static double retrievability(double elapsedDays, double stability) {
        return Math.pow(1 + FACTOR * Math.max(0.0, elapsedDays) / stability, DECAY);
    }

    static double nextStability(double stability, double difficulty, double elapsedDays, int rating) {
        double r = retrievability(elapsedDays, stability);
        if (rating == AGAIN) {
            return clampStability(Math.min(stability, forgetStability(difficulty, stability, r)));
        }
        return clampStability(recallStability(difficulty, stability, r, rating));
    }

    /** 成功回忆：S' = S * (1 + e^w8 * (11-D) * S^-w9 * (e^(w10*(1-R)) - 1) * hardPenalty * easyBonus)。 */
    static double recallStability(double difficulty, double stability, double retrievability, int rating) {
        double hardPenalty = rating == HARD ? W[15] : 1.0;
        double easyBonus = rating == EASY ? W[16] : 1.0;
        double factor = 1
                + Math.exp(W[8])
                * (11.0 - difficulty)
                * Math.pow(stability, -W[9])
                * (Math.exp((1.0 - retrievability) * W[10]) - 1.0)
                * hardPenalty
                * easyBonus;
        return clampStability(stability * factor);
    }

    /** 遗忘后：S' = w11 * D^-w12 * ((S+1)^w13 - 1) * e^(w14*(1-R))。 */
    static double forgetStability(double difficulty, double stability, double retrievability) {
        return clampStability(W[11]
                * Math.pow(difficulty, -W[12])
                * (Math.pow(stability + 1.0, W[13]) - 1.0)
                * Math.exp((1.0 - retrievability) * W[14]));
    }

    static double clampStability(double stability) {
        return Math.min(Math.max(stability, S_MIN), S_MAX);
    }
}
