package com.chutiji.review;

import java.util.List;

/**
 * 答对时的评分映射（设计文档 10.3 裁定 2.2）：
 * 用时 &lt; 历史 P25 记 Easy，&gt; P75 记 Hard，其余 Good。
 */
public final class ReviewRatingPolicy {

    /** 样本太少时百分位没有意义，直接记 Good。 */
    static final int MIN_SAMPLES = 4;

    private ReviewRatingPolicy() {
    }

    public static int rateForCorrect(List<Integer> durationsMs, Integer durationMs) {
        if (durationMs == null || durationsMs == null || durationsMs.size() < MIN_SAMPLES) {
            return FsrsScheduler.GOOD;
        }
        List<Integer> sorted = durationsMs.stream().sorted().toList();
        double p25 = percentile(sorted, 0.25);
        double p75 = percentile(sorted, 0.75);
        if (durationMs < p25) {
            return FsrsScheduler.EASY;
        }
        if (durationMs > p75) {
            return FsrsScheduler.HARD;
        }
        return FsrsScheduler.GOOD;
    }

    /** 线性插值百分位，与常见的 inclusive 定义一致。 */
    static double percentile(List<Integer> sorted, double fraction) {
        if (sorted.isEmpty()) {
            return 0;
        }
        double index = fraction * (sorted.size() - 1);
        int lower = (int) Math.floor(index);
        int upper = (int) Math.ceil(index);
        if (lower == upper) {
            return sorted.get(lower);
        }
        double weight = index - lower;
        return sorted.get(lower) * (1 - weight) + sorted.get(upper) * weight;
    }
}
