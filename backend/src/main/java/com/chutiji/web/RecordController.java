package com.chutiji.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.chutiji.common.ApiResult;
import com.chutiji.record.RecordDtos.RecordPage;
import com.chutiji.record.RecordService;
import com.chutiji.security.CurrentUser;

/** 做题记录分页。路径 /api/records 已被 AuthInterceptor 的 /api/** 覆盖。 */
@RestController
@RequestMapping("/api/records")
public class RecordController {

    private final RecordService recordService;

    public RecordController(RecordService recordService) {
        this.recordService = recordService;
    }

    @GetMapping
    public ApiResult<RecordPage> list(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size) {
        return ApiResult.ok(recordService.list(CurrentUser.requireId(), page, size));
    }
}
