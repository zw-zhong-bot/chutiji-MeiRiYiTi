package com.chutiji.pool;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.chutiji.pool.PoolDtos.GeneratePoolView;

/**
 * 题库预生成跑批：按 app.pool.targets 配置的档位逐档补题。
 *
 * <p>默认关闭（app.pool.schedule-enabled=false）：池题是资产，往库里灌什么由人决定。
 * 接口 {@code POST /api/pool/generate} 是随时可用的手动入口，与这里的定时入口共用
 * {@link PoolService#generate}，保证两条路落库形状完全一致。</p>
 */
@Component
public class PoolBatchJob {

    private static final Logger log = LoggerFactory.getLogger(PoolBatchJob.class);

    private final PoolService poolService;
    private final PoolProperties properties;

    public PoolBatchJob(PoolService poolService, PoolProperties properties) {
        this.poolService = poolService;
        this.properties = properties;
    }

    /** 每天 04:15 触发；开关与 cron 都走 app.pool.*。 */
    @org.springframework.scheduling.annotation.Scheduled(cron = "${app.pool.cron:0 15 4 * * *}")
    public void scheduledFill() {
        if (!properties.isScheduleEnabled()) {
            return;
        }
        runOnce();
    }

    /**
     * 跑一轮配置里的全部档位。
     *
     * @return 各档合计的 generated / skipped
     */
    public GeneratePoolView runOnce() {
        int generated = 0;
        int skipped = 0;
        int failedTargets = 0;
        for (PoolProperties.Target target : properties.getTargets()) {
            if (target.getModuleKey() == null || target.getModuleKey().isBlank()) {
                log.warn("跳过未配置 moduleKey 的池题档位");
                failedTargets++;
                continue;
            }
            try {
                GeneratePoolView result = poolService.generate(target.getModuleKey(), target.getDifficulty(),
                        target.getCount() == null ? 0 : target.getCount());
                generated += result.generated();
                skipped += result.skipped();
            } catch (RuntimeException ex) {
                // 单档失败（模块不存在、生成器报错）不影响其他档位
                failedTargets++;
                log.warn("池题档位跑批失败 module={}：{}", target.getModuleKey(), ex.getMessage());
            }
        }
        log.info("池题定时跑批结束 generated={} skipped={} failedTargets={}", generated, skipped, failedTargets);
        return new GeneratePoolView(generated, skipped);
    }

    public PoolProperties getProperties() {
        return properties;
    }
}
