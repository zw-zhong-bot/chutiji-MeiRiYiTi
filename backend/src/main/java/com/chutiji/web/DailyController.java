package com.chutiji.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.chutiji.common.ApiResult;
import com.chutiji.pool.DailyQuestionService;
import com.chutiji.question.QuestionDtos.QuestionView;
import com.chutiji.security.CurrentUser;

/** 每日一题：同一天同一用户固定一道题。 */
@RestController
@RequestMapping("/api/daily")
public class DailyController {

    private final DailyQuestionService dailyQuestionService;

    public DailyController(DailyQuestionService dailyQuestionService) {
        this.dailyQuestionService = dailyQuestionService;
    }

    @GetMapping
    public ApiResult<QuestionView> today() {
        return ApiResult.ok(dailyQuestionService.today(CurrentUser.requireId()));
    }
}
