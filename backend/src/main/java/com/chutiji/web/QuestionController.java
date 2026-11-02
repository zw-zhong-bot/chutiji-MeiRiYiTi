package com.chutiji.web;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.chutiji.common.ApiResult;
import com.chutiji.question.GradeService;
import com.chutiji.question.QuestionDtos.CreateQuestionRequest;
import com.chutiji.question.QuestionDtos.GradeAnswerRequest;
import com.chutiji.question.QuestionDtos.GradeView;
import com.chutiji.question.QuestionDtos.QuestionView;
import com.chutiji.question.QuestionService;
import com.chutiji.security.CurrentUser;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/questions")
public class QuestionController {

    private final QuestionService questionService;
    private final GradeService gradeService;

    public QuestionController(QuestionService questionService, GradeService gradeService) {
        this.questionService = questionService;
        this.gradeService = gradeService;
    }

    @PostMapping
    public ApiResult<QuestionView> create(@Valid @RequestBody CreateQuestionRequest request) {
        return ApiResult.ok(questionService.create(CurrentUser.requireId(), request));
    }

    @PostMapping("/{id}/grade")
    public ApiResult<GradeView> grade(@PathVariable("id") String id,
                                      @Valid @RequestBody GradeAnswerRequest request) {
        return ApiResult.ok(gradeService.grade(CurrentUser.requireId(), id, request));
    }
}
