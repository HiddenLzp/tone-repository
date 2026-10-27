package com.lizp.tone.module.agent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 语气 Agent 配置，对应 application.yml 的 {@code tone.agent.*}。
 */
@Data
@Component
@ConfigurationProperties(prefix = "tone.agent")
public class AgentProperties {

    /** ReAct 循环最大轮数 */
    private int maxRounds = 8;

    /** 单次提交文本最大长度（字符） */
    private int maxTextLength = 2000;

    /** 每日免费检查次数（基于 openId + 日期） */
    private int dailyFreeCount = 3;

    /** 每日通过看广告最多可获得的额外次数 */
    private int dailyRewardMax = 10;
}
