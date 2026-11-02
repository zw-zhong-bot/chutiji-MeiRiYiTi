package com.chutiji.review;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 答对时的用时映射：< P25 → Easy，> P75 → Hard，其余 Good。 */
class ReviewRatingPolicyTest {

    @Test
    @DisplayName("用时低于 P25 记 Easy，高于 P75 记 Hard，中间记 Good")
    void mapsDurationToRating() {
        // 0,10,20,30,40,50,60,70,80,90 → P25=22.5, P75=67.5
        List<Integer> samples = List.of(0, 10, 20, 30, 40, 50, 60, 70, 80, 90);

        assertEquals(FsrsScheduler.EASY, ReviewRatingPolicy.rateForCorrect(samples, 10), "秒答应记 Easy");
        assertEquals(FsrsScheduler.GOOD, ReviewRatingPolicy.rateForCorrect(samples, 40), "正常应记 Good");
        assertEquals(FsrsScheduler.HARD, ReviewRatingPolicy.rateForCorrect(samples, 90), "超时应记 Hard");
    }

    @Test
    @DisplayName("样本不足或没有用时一律记 Good")
    void fallsBackToGood() {
        assertEquals(FsrsScheduler.GOOD, ReviewRatingPolicy.rateForCorrect(List.of(1, 2, 3), 1_000_000));
        assertEquals(FsrsScheduler.GOOD, ReviewRatingPolicy.rateForCorrect(List.of(), 5));
        assertEquals(FsrsScheduler.GOOD, ReviewRatingPolicy.rateForCorrect(
                List.of(0, 10, 20, 30, 40, 50, 60, 70, 80, 90), null));
    }

    @Test
    @DisplayName("百分位用线性插值")
    void percentileUsesLinearInterpolation() {
        assertEquals(22.5, ReviewRatingPolicy.percentile(List.of(0, 10, 20, 30, 40, 50, 60, 70, 80, 90), 0.25), 1e-9);
        assertEquals(67.5, ReviewRatingPolicy.percentile(List.of(0, 10, 20, 30, 40, 50, 60, 70, 80, 90), 0.75), 1e-9);
    }
}
