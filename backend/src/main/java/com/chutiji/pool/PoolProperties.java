package com.chutiji.pool;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 题库跑批配置（app.pool.*）。
 *
 * <p>定时跑批默认关闭：池题是资产，什么时候补、补哪些档由人决定，
 * 不在没人看管的情况下自动往库里灌题。</p>
 */
@Component
@ConfigurationProperties(prefix = "app.pool")
public class PoolProperties {

    /** 定时跑批开关，默认关。 */
    private boolean scheduleEnabled = false;

    /** 定时跑批 cron，默认每天 04:15。 */
    private String cron = "0 15 4 * * *";

    /** 每档默认生成条数。 */
    private int defaultCount = PoolService.DEFAULT_COUNT;

    /** 「同一用户近期不重复拿到同一道池题」的回看天数。 */
    private int recentDays = 7;

    /** 定时跑批的目标档位；为空时定时任务什么都不做。 */
    private List<Target> targets = new ArrayList<>();

    public boolean isScheduleEnabled() {
        return scheduleEnabled;
    }

    public void setScheduleEnabled(boolean scheduleEnabled) {
        this.scheduleEnabled = scheduleEnabled;
    }

    public String getCron() {
        return cron;
    }

    public void setCron(String cron) {
        this.cron = cron;
    }

    public int getDefaultCount() {
        return defaultCount;
    }

    public void setDefaultCount(int defaultCount) {
        this.defaultCount = defaultCount;
    }

    public int getRecentDays() {
        return recentDays;
    }

    public void setRecentDays(int recentDays) {
        this.recentDays = recentDays;
    }

    public List<Target> getTargets() {
        return targets;
    }

    public void setTargets(List<Target> targets) {
        this.targets = targets == null ? new ArrayList<>() : targets;
    }

    /** 一个「模块 × 难度配置」档位。count 为空时用 defaultCount。 */
    public static class Target {

        private String moduleKey;

        private Map<String, Object> difficulty = new LinkedHashMap<>();

        private Integer count;

        public String getModuleKey() {
            return moduleKey;
        }

        public void setModuleKey(String moduleKey) {
            this.moduleKey = moduleKey;
        }

        public Map<String, Object> getDifficulty() {
            return difficulty;
        }

        public void setDifficulty(Map<String, Object> difficulty) {
            this.difficulty = difficulty == null ? new LinkedHashMap<>() : difficulty;
        }

        public Integer getCount() {
            return count;
        }

        public void setCount(Integer count) {
            this.count = count;
        }
    }
}
