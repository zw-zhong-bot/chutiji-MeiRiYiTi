package com.chutiji.record;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.chutiji.question.AnswerRecordEntity;
import com.chutiji.question.AnswerRecordRepository;
import com.chutiji.record.RecordDtos.RecordItem;
import com.chutiji.record.RecordDtos.RecordPage;

/** 做题记录查询：一律按传入的 userId 过滤，调用方必须传当前登录用户。 */
@Service
public class RecordService {

    /** 每页条数上限，超出按上限处理而不是报错。 */
    public static final int MAX_PAGE_SIZE = 100;

    private final AnswerRecordRepository answerRecordRepository;

    public RecordService(AnswerRecordRepository answerRecordRepository) {
        this.answerRecordRepository = answerRecordRepository;
    }

    @Transactional(readOnly = true)
    public RecordPage list(Long userId, int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);

        // created_at 倒序；id 倒序作次级排序，保证同一秒内的记录顺序稳定（分页不重不漏）
        Pageable pageable = PageRequest.of(safePage, safeSize,
                Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id")));

        Page<AnswerRecordEntity> result = answerRecordRepository.findByUserId(userId, pageable);

        // 手工映射成白名单 DTO：submitted 不在 RecordItem 里，从结构上排除泄露
        List<RecordItem> items = result.getContent().stream()
                .map(e -> new RecordItem(e.getId(), e.getQuestionId(), e.getModuleKey(),
                        Boolean.TRUE.equals(e.getCorrect()), e.getDurationMs(), e.getCreatedAt()))
                .toList();

        return new RecordPage(items, safePage, safeSize, result.getTotalElements(), result.getTotalPages());
    }
}
