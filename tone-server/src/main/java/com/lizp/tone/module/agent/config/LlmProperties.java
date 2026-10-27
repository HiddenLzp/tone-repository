package com.lizp.tone.module.agent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * LLM 配置，对应 application.yml 的 {@code tone.llm.*}。
 *
 * <p>统一走 OpenAI 兼容协议，主流厂商只需换 base-url / model / api-key：
 * <ul>
 *   <li>智谱：https://open.bigmodel.cn/api/paas/v4 + glm-4-flash / glm-4-plus</li>
 *   <li>通义（DashScope 兼容模式）：https://dashscope.aliyuncs.com/compatible-mode/v1 + qwen-plus</li>
 * </ul>
 */
@Data
@Component
@ConfigurationProperties(prefix = "tone.llm")
public class LlmProperties {

    /** true = 使用 MockChatModel（无需 Key，返回模拟结果，用于开发联调） */
    private boolean mock = true;

    /** 仅决定 base-url/model 的默认预设：zhipu / dashscope / custom */
    private String provider = "zhipu";

    private String baseUrl;

    /** 生产环境通过环境变量 TONE_LLM_API_KEY 注入 */
    private String apiKey;

    private String model;

    private Double temperature = 0.7;

    /** 连接超时（毫秒） */
    private long connectTimeout = 5000;

    /** 读超时（毫秒），Agent 多轮调用耗时更长 */
    private long readTimeout = 60000;

    /** 备用模型降级配置（主模型失败时自动换它重试一次，见 FallbackChatModel） */
    private Fallback fallback = new Fallback();

    /**
     * 备用模型：默认与主模型同一端点；百炼专属端点只服务专属模型时，
     * 给 fallback 配公共服务端点即可（如 dashscope compatible-mode + qwen-flash）。
     */
    @Data
    public static class Fallback {
        /** 备用模型名；为空 = 不启用降级 */
        private String model = "";
        /** 备用端点；为空 = 复用主 base-url */
        private String baseUrl = "";
    }
}
