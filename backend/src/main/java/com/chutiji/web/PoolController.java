package com.chutiji.web;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.chutiji.common.ApiResult;
import com.chutiji.pool.PoolDtos.GeneratePoolRequest;
import com.chutiji.pool.PoolDtos.GeneratePoolView;
import com.chutiji.pool.PoolService;

import jakarta.validation.Valid;

/** 题库管理：登录即可，不做管理员体系。 */
@RestController
@RequestMapping("/api/pool")
public class PoolController {

    private final PoolService poolService;

    public PoolController(PoolService poolService) {
        this.poolService = poolService;
    }

    @PostMapping("/generate")
    public ApiResult<GeneratePoolView> generate(@Valid @RequestBody GeneratePoolRequest request) {
        return ApiResult.ok(poolService.generate(request.moduleKey(), request.difficulty(),
                request.count() == null ? 0 : request.count()));
    }
}
