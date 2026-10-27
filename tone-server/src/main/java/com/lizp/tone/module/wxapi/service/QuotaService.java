package com.lizp.tone.module.wxapi.service;

import com.lizp.tone.common.BaseException;
import com.lizp.tone.common.ErrorCode;
import com.lizp.tone.module.agent.service.AgentRuntimeConfigService;
import com.lizp.tone.module.wxapi.entity.AppAdReward;
import com.lizp.tone.module.wxapi.entity.AppQuotaEvent;
import com.lizp.tone.module.wxapi.mapper.AppAdRewardMapper;
import com.lizp.tone.module.wxapi.mapper.AppQuotaEventMapper;
import com.lizp.tone.utils.RedisUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 配额管理：每日免费 N 次 + 看广告得额外次数。
 *
 * <p>两层分工：Redis 计数（openId + 日期维度，TTL 48h）只负责"今天还剩几次"的实时判定；
 * 所有需要统计的事实（消耗/退还/拒绝/领取）落 {@code app_quota_event} 流水，
 * 看板配额漏斗单一来源走 MySQL，Redis 过期不影响统计。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QuotaService {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final long KEY_TTL_SECONDS = 48 * 3600L;

    private final RedisUtils redisUtils;
    /** 运行时配额配置：管理台「运营设置」可在线改，无需重启 */
    private final AgentRuntimeConfigService runtimeConfig;
    private final AppAdRewardMapper appAdRewardMapper;
    private final AppQuotaEventMapper appQuotaEventMapper;

    public record QuotaSnapshot(int freeUsed, int freeTotal, int rewardClaimed, int rewardUsed) {
        public int remain() {
            return Math.max(0, freeTotal - freeUsed) + Math.max(0, rewardClaimed - rewardUsed);
        }
    }

    /** 消费一次检查机会；成功返回消费的桶（free / reward），失败抛配额不足 */
    public String consume(String openId) {
        String day = LocalDate.now().format(DAY);
        long freeUsed = redisUtils.incrWithExpire(key("quota:free", openId, day), KEY_TTL_SECONDS);
        if (freeUsed <= runtimeConfig.dailyFreeCount()) {
            recordEvent(openId, "consume", "free");
            return "free";
        }
        // 免费用尽 → 消费已领取的奖励
        long claimed = getNum(key("quota:reward", openId, day));
        long used = getNum(key("quota:reward_used", openId, day));
        if (claimed - used > 0) {
            redisUtils.incrWithExpire(key("quota:reward_used", openId, day), KEY_TTL_SECONDS);
            recordEvent(openId, "consume_reward", "reward");
            return "reward";
        }
        // 没有可用奖励，回滚刚才的 free 计数，抛配额不足
        redisUtils.decr(key("quota:free", openId, day));
        recordEvent(openId, "reject", null);
        throw new BaseException(ErrorCode.QUOTA_EXHAUSTED);
    }

    /** Agent 执行失败时退还刚才消费的机会（流水记 refund，漏斗按净值统计） */
    public void refund(String openId, String bucket) {
        String day = LocalDate.now().format(DAY);
        if ("reward".equals(bucket)) {
            redisUtils.decr(key("quota:reward_used", openId, day));
        } else {
            redisUtils.decr(key("quota:free", openId, day));
        }
        recordEvent(openId, "refund", bucket);
    }

    /** 看完激励视频后领取 1 次奖励 */
    public QuotaSnapshot claimReward(String openId, String unitId) {
        String day = LocalDate.now().format(DAY);
        long claimed = redisUtils.incrWithExpire(key("quota:reward", openId, day), KEY_TTL_SECONDS);
        if (claimed > runtimeConfig.dailyRewardMax()) {
            redisUtils.decr(key("quota:reward", openId, day));
            recordEvent(openId, "claim", "over-limit");
            throw new BaseException(ErrorCode.REWARD_LIMIT_REACHED);
        }
        String resolvedUnitId = unitId == null || unitId.isBlank() ? "dev" : unitId;
        AppAdReward reward = new AppAdReward();
        reward.setOpenId(openId);
        reward.setRewardDate(LocalDate.now());
        reward.setUnitId(resolvedUnitId);
        appAdRewardMapper.insert(reward);
        recordEvent(openId, "claim", resolvedUnitId);
        return snapshot(openId);
    }

    public QuotaSnapshot snapshot(String openId) {
        String day = LocalDate.now().format(DAY);
        int freeUsed = (int) getNum(key("quota:free", openId, day));
        int claimed = (int) getNum(key("quota:reward", openId, day));
        int rewardUsed = (int) getNum(key("quota:reward_used", openId, day));
        return new QuotaSnapshot(Math.min(freeUsed, runtimeConfig.dailyFreeCount()),
                runtimeConfig.dailyFreeCount(), claimed, rewardUsed);
    }

    /**
     * 开发辅助：清空当前 openId 今天的全部配额计数（免费 / 已领奖励 / 已用奖励三个桶），
     * 相当于"明天再来"的即时版，供开发联调反复触发配额流程。
     *
     * <p>边界说明：只删 Redis 判定键，**不动两张流水表**——
     * app_quota_event 是看板漏斗的统计事实源、app_ad_reward 是真实领取日志，
     * 重置是纯开发行为，不应污染统计（所以重置后漏斗数字不会回退，属预期）。
     * 调用方（DevQuotaController）有 tone.dev-tools-enabled 护栏，生产装配不出来。
     */
    public QuotaSnapshot reset(String openId) {
        String day = LocalDate.now().format(DAY);
        redisUtils.delete(
                key("quota:free", openId, day),
                key("quota:reward", openId, day),
                key("quota:reward_used", openId, day));
        return snapshot(openId);
    }

    /** 流水埋点：统计通道故障绝不能影响配额主流程 */
    private void recordEvent(String openId, String eventType, String detail) {
        try {
            AppQuotaEvent event = new AppQuotaEvent();
            event.setOpenId(openId);
            event.setEventDate(LocalDate.now());
            event.setEventType(eventType);
            event.setDetail(detail);
            appQuotaEventMapper.insert(event);
        } catch (Exception e) {
            log.warn("配额事件流水写入失败 type={} openId={}", eventType, openId, e);
        }
    }

    private long getNum(String key) {
        Object v = redisUtils.get(key);
        return v instanceof Number n ? n.longValue() : 0;
    }


    private String key(String prefix, String openId, String day) {
        return prefix + ":" + openId + ":" + day;
    }
}
