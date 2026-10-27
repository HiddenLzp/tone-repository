package com.lizp.tone.module.agent.service;

import com.lizp.tone.module.agent.config.AgentProperties;
import com.lizp.tone.module.agent.config.LlmProperties;
import com.lizp.tone.module.system.config.SysConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * Agent 运行时配置：sys_config（管理台「运营设置」页可改）优先，yml 为默认值。
 *
 * <p>为什么经 sys_config 而不是直接 @RefreshScope：模板的参数体系自带
 * 30 分钟 Redis 缓存 + 修改即清缓存，读多写少场景下既轻又即时生效；
 * 且管理台已有参数管理的权限/审计链路，不必再造热刷新机制。
 *
 * <p>覆盖项：模型 / 温度 / 备用模型 / 每日免费次数 / 每日奖励上限 / Token 告警阈值。
 * 注意模型相关项通过 per-request options 生效（见 optionsBuilder() 的调用方），
 * 切换无需重建 ChatModel bean。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentRuntimeConfigService {

    public static final String KEY_MODEL = "agent.llm.model";
    public static final String KEY_TEMPERATURE = "agent.llm.temperature";
    public static final String KEY_FALLBACK_MODEL = "agent.llm.fallbackModel";
    public static final String KEY_DAILY_FREE = "agent.quota.dailyFreeCount";
    public static final String KEY_DAILY_REWARD_MAX = "agent.quota.dailyRewardMax";
    public static final String KEY_TOKEN_ALERT = "agent.llm.tokenAlertThreshold";

    private final SysConfigService sysConfigService;
    private final LlmProperties llmProperties;
    private final AgentProperties agentProperties;

    /** 当前生效的主模型名 */
    public String model() {
        return sysConfigService.getValue(KEY_MODEL, llmProperties.getModel());
    }

    public double temperature() {
        String raw = sysConfigService.getValue(KEY_TEMPERATURE, String.valueOf(llmProperties.getTemperature()));
        try {
            return Math.max(0d, Math.min(2d, Double.parseDouble(raw)));
        } catch (NumberFormatException e) {
            log.warn("运行时温度配置非法（{}），回退 yml 默认", raw);
            return llmProperties.getTemperature() == null ? 0.7 : llmProperties.getTemperature();
        }
    }

    /** 当前生效的备用模型名（空 = 用 yml 启动值；yml 也没配则降级功能未启用） */
    public String fallbackModel() {
        return sysConfigService.getValue(KEY_FALLBACK_MODEL, llmProperties.getFallback().getModel());
    }

    public int dailyFreeCount() {
        return parseInt(sysConfigService.getValue(KEY_DAILY_FREE,
                String.valueOf(agentProperties.getDailyFreeCount())), agentProperties.getDailyFreeCount(), 1, 100);
    }

    public int dailyRewardMax() {
        return parseInt(sysConfigService.getValue(KEY_DAILY_REWARD_MAX,
                String.valueOf(agentProperties.getDailyRewardMax())), agentProperties.getDailyRewardMax(), 0, 1000);
    }

    /** Token 日告警阈值（当日总消耗 ≥ 阈值时看板亮红横幅 + 定时任务打 WARN）；0 = 关闭 */
    public long tokenAlertThreshold() {
        String raw = sysConfigService.getValue(KEY_TOKEN_ALERT, "0");
        try {
            return Math.max(0L, Long.parseLong(raw));
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    /**
     * per-request 选项（模型/温度随运行时配置走）。
     * 返回 builder：ChatClient.options(...) 需要 builder、new Prompt(text, options) 需要
     * 构造好的对象——两种形态各取所需，避免 builder 复用歧义。
     */
    public OpenAiChatOptions.Builder optionsBuilder() {
        return OpenAiChatOptions.builder().model(model()).temperature(temperature());
    }

    public OpenAiChatOptions newOptions() {
        return optionsBuilder().build();
    }

    /** 有无运行时覆盖（管理台展示用：区分"默认值"与"已覆盖"） */
    public boolean isOverridden(String key) {
        return StringUtils.hasText(sysConfigService.getValue(key, ""));
    }

    private int parseInt(String raw, int defaultValue, int min, int max) {
        try {
            return Math.max(min, Math.min(max, Integer.parseInt(raw)));
        } catch (NumberFormatException e) {
            log.warn("运行时配置 [{}] 非法（{}），回退默认 {}", KEY_DAILY_FREE, raw, defaultValue);
            return defaultValue;
        }
    }
}
