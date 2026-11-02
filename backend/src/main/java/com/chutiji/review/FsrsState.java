package com.chutiji.review;

/**
 * 复习卡的 FSRS 记忆状态。
 *
 * @param stability      记忆稳定度 S（> 0），遗忘到 90% 所需天数
 * @param difficultyFsrs 该知识点难度 D，范围 [1, 10]
 * @param reps           累计复习次数
 * @param lapses         累计遗忘次数
 * @param state          NEW / LEARNING / REVIEW / RELEARNING
 */
public record FsrsState(double stability, double difficultyFsrs, int reps, int lapses, String state) {

    public static final String NEW = "NEW";
    public static final String LEARNING = "LEARNING";
    public static final String REVIEW = "REVIEW";
    public static final String RELEARNING = "RELEARNING";

    /** 从未复习过的卡：S=0、D 取 DDL 默认值。 */
    public static FsrsState fresh() {
        return new FsrsState(0.0, FsrsScheduler.DEFAULT_DIFFICULTY, 0, 0, NEW);
    }

    /** 未学过：状态是 NEW，或稳定度尚未建立（S=0 时记忆曲线无定义）。 */
    public boolean isNew() {
        return NEW.equals(state) || stability <= 0.0;
    }
}
