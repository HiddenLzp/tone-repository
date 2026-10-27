package com.lizp.tone.module.stats.controller;

import com.lizp.tone.common.R;
import com.lizp.tone.module.stats.mapper.StatsMapper;
import com.lizp.tone.security.authz.RequiresPermissions;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 数据看板：今日概览（含昨日对比）/ 趋势 / 场景分布 / Token 排行 / 广告观看数。
 */
@Tag(name = "数据看板")
@RestController
@RequestMapping("/agent/stats")
@RequiredArgsConstructor
public class StatsController {

    private final StatsMapper statsMapper;
    /** 运行时配置：Token 告警阈值来自运营设置页（sys_config），看板横幅据此亮红 */
    private final com.lizp.tone.module.agent.service.AgentRuntimeConfigService runtimeConfig;

    @GetMapping("/overview")
    @RequiresPermissions("agent:stats:view")
    @Operation(summary = "今日概览：活跃用户 / 请求量（含失败与成功率）/ Token / 平均轮次 / 广告观看，附昨日对比")
    public R<Map<String, Object>> overview() {
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        LocalDateTime tomorrowStart = todayStart.plusDays(1);
        StatsMapper.OverviewRow today = statsMapper.overview(todayStart, tomorrowStart);
        StatsMapper.OverviewRow yesterday = statsMapper.overview(todayStart.minusDays(1), todayStart);

        Map<String, Object> data = new HashMap<>();
        data.put("activeUsers", today.getActiveUsers());
        data.put("requests", today.getRequests());
        data.put("errorRequests", today.getErrorRequests());
        long done = nz(today.getRequests());
        long errors = nz(today.getErrorRequests());
        data.put("successRate", done + errors == 0 ? null : Math.round(done * 1000.0 / (done + errors)) / 10.0);
        data.put("promptTokens", today.getPromptTokens());
        data.put("completionTokens", today.getCompletionTokens());
        data.put("totalTokens", nz(today.getPromptTokens()) + nz(today.getCompletionTokens()));
        data.put("avgRounds", today.getAvgRounds() == null ? null : Math.round(today.getAvgRounds() * 10.0) / 10.0);
        data.put("adViews", statsMapper.adViews(LocalDate.now()));
        // Token 日告警：阈值来自运营设置（0=关闭）；超限时看板顶部亮红横幅
        long threshold = runtimeConfig.tokenAlertThreshold();
        data.put("tokenAlertThreshold", threshold);
        data.put("tokenAlertExceeded", threshold > 0
                && nz(today.getPromptTokens()) + nz(today.getCompletionTokens()) >= threshold);
        data.put("yesterdayRequests", yesterday.getRequests());
        data.put("yesterdayActiveUsers", yesterday.getActiveUsers());
        return R.ok(data);
    }

    @GetMapping("/trend")
    @RequiresPermissions("agent:stats:view")
    @Operation(summary = "最近 N 天请求量与 Token 消耗趋势")
    public R<List<StatsMapper.TrendRow>> trend(@RequestParam(defaultValue = "7") int days) {
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        return R.ok(statsMapper.trend(todayStart.minusDays(Math.max(1, Math.min(days, 90)) - 1L), todayStart.plusDays(1)));
    }

    @GetMapping("/scene-stats")
    @RequiresPermissions("agent:stats:view")
    @Operation(summary = "场景分布（最近 7 天完成请求）")
    public R<List<StatsMapper.SceneRow>> sceneStats() {
        return R.ok(statsMapper.sceneStats(LocalDate.now().minusDays(6).atStartOfDay()));
    }

    @GetMapping("/top-users")
    @RequiresPermissions("agent:stats:view")
    @Operation(summary = "Token 消耗最高的用户 Top N（优先展示昵称）")
    public R<List<StatsMapper.TopUserRow>> topUsers(@RequestParam(defaultValue = "20") int limit) {
        return R.ok(statsMapper.topUsers(Math.max(1, Math.min(limit, 100))));
    }

    @GetMapping("/quota-funnel")
    @RequiresPermissions("agent:stats:view")
    @Operation(summary = "配额漏斗（近 7 天）：免费消耗 → 用尽拒绝 → 广告领取 → 奖励消耗，消耗为退还后净值")
    public R<Map<String, Object>> quotaFunnel() {
        List<StatsMapper.QuotaEventRow> rows = statsMapper.quotaEvents(LocalDate.now().minusDays(6));
        long freeConsumed = 0;
        long refundFree = 0;
        long rewardConsumed = 0;
        long refundReward = 0;
        long rejected = 0;
        long claimed = 0;
        for (StatsMapper.QuotaEventRow row : rows) {
            long cnt = row.getCnt() == null ? 0 : row.getCnt();
            switch (row.getType()) {
                case "consume" -> freeConsumed += cnt;
                case "consume_reward" -> rewardConsumed += cnt;
                case "refund" -> {
                    if ("reward".equals(row.getDetail())) {
                        refundReward += cnt;
                    } else {
                        refundFree += cnt;
                    }
                }
                case "reject" -> rejected += cnt;
                case "claim" -> claimed += cnt;
                default -> {
                }
            }
        }
        // 人数按事件类型独立去重
        Map<String, Long> usersByType = new HashMap<>();
        for (StatsMapper.QuotaEventRow row : statsMapper.quotaEventUsers(LocalDate.now().minusDays(6))) {
            usersByType.put(row.getType(), row.getUsers());
        }
        Map<String, Object> data = new HashMap<>();
        data.put("freeConsumed", Math.max(0, freeConsumed - refundFree));
        data.put("rewardConsumed", Math.max(0, rewardConsumed - refundReward));
        data.put("rejected", rejected);
        data.put("rejectedUsers", usersByType.getOrDefault("reject", 0L));
        data.put("rewardClaimed", claimed);
        data.put("claimedUsers", usersByType.getOrDefault("claim", 0L));
        data.put("sinceDays", 7);
        return R.ok(data);
    }

    private long nz(Long v) {
        return v == null ? 0 : v;
    }
}
