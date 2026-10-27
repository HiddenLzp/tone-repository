package com.lizp.tone.module.agent.task;

import com.lizp.tone.module.agent.service.AgentRuntimeConfigService;
import com.lizp.tone.module.stats.mapper.StatsMapper;
import com.lizp.tone.utils.RedisUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Token 成本日告警任务：由模板的动态定时任务调度（sys_job 表配置 cron，
 * invoke_target = tokenAlertTask.checkDaily()，管理台「任务调度」可改周期/手动执行）。
 *
 * <p>动作：当日 Token 总消耗 ≥ 阈值（agent.token.alertThreshold，0=关闭）时
 * 打 WARN 日志（进任务日志可追溯）并写 Redis 标志（看板横幅读取同一份数据兜底）。
 * 个人项目没有短信/邮件通道，WARN + 看板红横幅就是"主动告警"的落地形态；
 * 以后接 webhook 只需在本方法里加一行调用。
 */
@Slf4j
@Component("tokenAlertTask")
@RequiredArgsConstructor
public class TokenAlertTask {

    private final StatsMapper statsMapper;
    private final AgentRuntimeConfigService runtimeConfig;
    private final RedisUtils redisUtils;

    /** 每天检查（cron 由 sys_job 决定）；public 无参，供 JobInvokeUtil 反射调用 */
    public void checkDaily() {
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        StatsMapper.OverviewRow row = statsMapper.overview(todayStart, todayStart.plusDays(1));
        long total = nz(row.getPromptTokens()) + nz(row.getCompletionTokens());
        long threshold = runtimeConfig.tokenAlertThreshold();
        if (threshold <= 0) {
            log.info("[Token告警] 未配置阈值（agent.token.alertThreshold），跳过检查");
            return;
        }
        if (total >= threshold) {
            log.warn("[Token告警] 今日 Token 消耗 {} 已达阈值 {}（请到数据看板核对成本）", total, threshold);
            redisUtils.set("agent:alert:token:" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE),
                    String.valueOf(total), Duration.ofHours(48));
        } else {
            log.info("[Token告警] 今日 Token 消耗 {} / 阈值 {}，正常", total, threshold);
        }
    }

    private long nz(Long v) {
        return v == null ? 0 : v;
    }
}
