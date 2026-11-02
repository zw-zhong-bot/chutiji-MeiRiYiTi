package com.chutiji.web;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.chutiji.common.ApiResult;
import com.chutiji.review.ReviewDtos.ReviewCardView;
import com.chutiji.review.ReviewDtos.ReviewStartView;
import com.chutiji.review.ReviewService;
import com.chutiji.review.ReviewStatsDtos.ReviewStatsView;
import com.chutiji.security.CurrentUser;

/** 复习队列。路径 /api/review/** 已被 AuthInterceptor 的 /api/** 覆盖。 */
@RestController
@RequestMapping("/api/review")
public class ReviewController {

    private final ReviewService reviewService;

    public ReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @GetMapping("/due")
    public ApiResult<List<ReviewCardView>> due() {
        return ApiResult.ok(reviewService.dueCards(CurrentUser.requireId(), LocalDateTime.now()));
    }

    @GetMapping("/cards")
    public ApiResult<List<ReviewCardView>> cards() {
        return ApiResult.ok(reviewService.allCards(CurrentUser.requireId(), LocalDateTime.now()));
    }

    /** 复习统计与最近 7 天复习量，全部按 CurrentUser 过滤。 */
    @GetMapping("/stats")
    public ApiResult<ReviewStatsView> stats() {
        return ApiResult.ok(reviewService.stats(CurrentUser.requireId(), LocalDateTime.now()));
    }

    @PostMapping("/{cardId}/start")
    public ApiResult<ReviewStartView> start(@PathVariable("cardId") Long cardId) {
        return ApiResult.ok(reviewService.start(CurrentUser.requireId(), cardId, LocalDateTime.now()));
    }
}
