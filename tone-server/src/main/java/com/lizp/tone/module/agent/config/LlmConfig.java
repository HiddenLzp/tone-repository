package com.lizp.tone.module.agent.config;

import com.openai.client.OpenAIClient;
import com.openai.client.OpenAIClientAsync;
import com.openai.client.OpenAIClientAsyncImpl;
import com.openai.client.OpenAIClientImpl;
import com.openai.core.ClientOptions;
import com.lizp.tone.module.agent.llm.FallbackChatModel;
import com.lizp.tone.module.agent.llm.MockChatModel;
import com.lizp.tone.module.agent.llm.RuntimeOptionsChatModel;
import com.lizp.tone.module.agent.service.AgentRuntimeConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.http.okhttp.SpringAiOpenAiHttpClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import java.time.Duration;

/**
 * ChatModel 装配：按 {@code tone.llm.mock} 在 Mock 与真实 OpenAI 兼容模型之间切换。
 *
 * <p>Spring AI 的 OpenAI 自动装配已被整体排除（见 application.yml），这里手动构建，
 * 用的是 Spring AI 2.0 基于官方 openai-java SDK 的新 API：
 * SpringAiOpenAiHttpClient(okhttp) → ClientOptions → OpenAIClient → OpenAiChatModel。
 * 两个坑：
 * 1. ClientOptions 的 httpClient 是必填项（openai-java-core 只有接口，实现在
 *    spring-ai-openai 的 SpringAiOpenAiHttpClient），漏掉报 "httpClient is required"；
 * 2. API Key 走 .credential(Credential) 或 .apiKey(String) 都可以，但为空时
 *    effectiveCredential 会报 "At least one credential source must be specified"，
 *    所以先做非空校验。
 * 智谱 / 通义等 OpenAI 兼容端点只需配置 base-url 即可复用。
 *
 * <p>降级（tone.llm.fallback.model 非空时启用）：主模型失败自动换备用模型重试一次
 * （见 FallbackChatModel）。备用模型名可在管理台运行时改，但降级功能的启停依赖
 * 启动配置——备用 bean 需要端点/凭据才能构建。
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class LlmConfig {

    private final LlmProperties llmProperties;
    private final AgentRuntimeConfigService runtimeConfig;

    @Bean
    public ChatModel chatModel() {
        log.info("LLM 装配：mock={}，provider={}，base-url={}，model={}，apiKey={}，fallback.model={}",
                llmProperties.isMock(), llmProperties.getProvider(), llmProperties.getBaseUrl(),
                llmProperties.getModel(),
                llmProperties.getApiKey() == null ? "null" : "已配置(len=" + llmProperties.getApiKey().length() + ")",
                llmProperties.getFallback().getModel());
        // 如果开启mock模式，则返回 MockChatModel
        if (llmProperties.isMock()) {
            return new MockChatModel();
        }
        // 校验 MODEL
        if (!StringUtils.hasText(llmProperties.getModel())) {
            throw new IllegalStateException("tone.llm.mock=false 时必须配置模型（环境变量 TONE_LLM_MODEL）");
        }

        // 校验 BASE_URL
        if (!StringUtils.hasText(llmProperties.getBaseUrl())) {
            throw new IllegalStateException("tone.llm.mock=false 时必须配置端点（环境变量 TONE_LLM_BASE_URL）");
        }

        // 校验 API KEY
        if (!StringUtils.hasText(llmProperties.getApiKey())) {
            throw new IllegalStateException("tone.llm.mock=false 时必须配置 API Key（环境变量 TONE_LLM_API_KEY）");
        }
        OpenAiChatModel primary = buildModel(llmProperties.getModel(), llmProperties.getBaseUrl());
        // 运行时注入包装：模型/温度从 sys_config 走（管理台在线切换），见 RuntimeOptionsChatModel 的注释
        RuntimeOptionsChatModel runtimeAware =
                new RuntimeOptionsChatModel(primary, runtimeConfig);
        if (!StringUtils.hasText(llmProperties.getFallback().getModel())) {
            return runtimeAware;
        }
        // 备用端点默认复用主端点；百炼专属端点只服务专属模型时，给 fallback.base-url 配公共服务端点
        String fbBaseUrl = StringUtils.hasText(llmProperties.getFallback().getBaseUrl())
                ? llmProperties.getFallback().getBaseUrl()
                : llmProperties.getBaseUrl();
        OpenAiChatModel fallback = buildModel(llmProperties.getFallback().getModel(), fbBaseUrl);
        log.info("LLM 降级已启用：主 [{}]@{} → 备 [{}]@{}",
                llmProperties.getModel(), llmProperties.getBaseUrl(),
                llmProperties.getFallback().getModel(), fbBaseUrl);
        return new FallbackChatModel(runtimeAware, fallback,
                llmProperties.getFallback().getModel(), runtimeConfig::fallbackModel);
    }

    /** 按模型名 + 端点构建一个完整的 OpenAiChatModel（主/备用共用同一套构建参数） */
    private OpenAiChatModel buildModel(String model, String baseUrl) {
        SpringAiOpenAiHttpClient httpClient = SpringAiOpenAiHttpClient.builder()
                .timeout(Duration.ofMillis(llmProperties.getReadTimeout()))
                .build();
        ClientOptions options = ClientOptions.builder()
                .httpClient(httpClient)
                .apiKey(llmProperties.getApiKey())
                .baseUrl(baseUrl)
                .timeout(Duration.ofMillis(llmProperties.getReadTimeout()))
                .build();
        // 只给 sync 客户端时，OpenAiChatModel.builder().build() 内部会用空凭证再建 async 客户端，
        // 导致 "At least one credential source must be specified"（OpenAiSetup.setupAsyncClient）。
        // sync/async 都显式提供，同一份 ClientOptions，绕开该隐式构建。
        return OpenAiChatModel.builder()
                .openAiClient(new OpenAIClientImpl(options))
                .openAiClientAsync(new OpenAIClientAsyncImpl(options))
                .options(OpenAiChatOptions.builder()
                        .model(model)
                        .temperature(llmProperties.getTemperature())
                        .build())
                .build();
    }
}
