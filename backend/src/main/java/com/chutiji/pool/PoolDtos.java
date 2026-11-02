package com.chutiji.pool;

import java.util.Map;

import jakarta.validation.constraints.NotBlank;

/** 题库跑批的出入参。 */
public final class PoolDtos {

    private PoolDtos() {
    }

    /** count 为空时按每档默认条数跑（见 PoolService.DEFAULT_COUNT）。 */
    public record GeneratePoolRequest(
            @NotBlank(message = "moduleKey 不能为空") String moduleKey,
            Map<String, Object> difficulty,
            Integer count) {
    }

    /** generated 是真正入库的条数；skipped 是生成抛错被跳过的条数。 */
    public record GeneratePoolView(int generated, int skipped) {
    }
}
