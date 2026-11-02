package com.chutiji.review;

import java.util.List;

/**
 * 复习统计的出参（设计文档 4.2 的 GET /api/review/stats）。
 *
 * <p>全部字段都是只读聚合，不含任何用户内部信息；数据按当前登录用户过滤。
 */
public final class ReviewStatsDtos {

    private ReviewStatsDtos() {
    }

    /** 按模块的分布：卡片数 + 该模块卡片累计的复习次数与遗忘次数。 */
    public record ModuleStat(String moduleKey, int cards, int reviews, int lapses) {
    }

    /**
     * 单个自然日：reviews 是当天的评分条数，correct 是其中 rating &gt; 1 的条数
     * （rating=1 即 Again，代表答错）。
     */
    public record DayStat(String date, int reviews, int correct) {
    }

    /**
     * @param newCards    状态仍为 NEW 的卡片（从未复习过）
     * @param reviewCards totalCards - newCards
     * @param avgStability 卡片 stability 的算术平均，保留两位小数
     * @param last7Days    最近 7 个自然日（含今天），无数据的日期补 0
     */
    public record ReviewStatsView(
            int totalCards,
            int dueCards,
            int newCards,
            int reviewCards,
            int totalReviews,
            int totalLapses,
            double avgStability,
            List<ModuleStat> byModule,
            List<DayStat> last7Days) {
    }
}
