package com.chutiji.record;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 做题记录出入参。
 *
 * <p>刻意不包含 submitted 原始答案：设计文档 4.2 未要求暴露，且属于用户输入内容。</p>
 */
public final class RecordDtos {

    private RecordDtos() {
    }

    /** 单条做题记录，字段白名单，绝不回传 submitted。 */
    public record RecordItem(
            Long id,
            String questionId,
            String moduleKey,
            boolean correct,
            Integer durationMs,
            LocalDateTime createdAt) {
    }

    /** 分页结果：page 从 0 开始，size 为归一化后的实际每页条数。 */
    public record RecordPage(
            List<RecordItem> items,
            int page,
            int size,
            long total,
            int totalPages) {
    }
}
