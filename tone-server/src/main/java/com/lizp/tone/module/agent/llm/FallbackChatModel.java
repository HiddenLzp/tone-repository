package com.lizp.tone.module.agent.llm;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;
import reactor.core.publisher.Flux;

import java.util.function.Supplier;

/**
 * LLM 自动降级包装：主模型失败（超时/429 限流/5xx/模型名错误等）时，自动换备用模型
 * 重试一次，而不是直接报错退配额——大杯模型（qwen3.8-max 单次 60s+）偶发超时率高，
 * 有 flash 档兜底能把"服务开小差"变成用户无感的成功。
 *
 * <p>降级时的模型名改写：运行时配置（agent.llm.fallbackModel，见 AgentRuntimeConfigService）
 * 优先于 yml 启动值。主调用的 Prompt 带着主模型的 per-request options，直接转发给
 * 备用 bean 会让备用端点收到"主模型名"的请求，所以降级分支必须把 options 的 model
 * 字段改写成备用模型名再发（仅本项目自己构造的 OpenAiChatOptions，字段可控）。
 *
 * <p>为什么不用 Spring AI 自带的 RetryTemplate：那是"同模型原地重试"，对超时/限流
 * 无效（再等 60 秒还是超时），跨模型降级必须自己包。
 */
@Slf4j
public class FallbackChatModel implements ChatModel {

    private final ChatModel primary;
    private final ChatModel fallback;
    /** 备用模型名供应器：运行时配置优先，取不到回退到构造时的启动值 */
    private final Supplier<String> fallbackModelSupplier;
    /** 启动时 yml 里的备用模型名（supplier 返回空时的兜底） */
    private final String defaultFallbackModel;

    public FallbackChatModel(ChatModel primary, ChatModel fallback,
                             String defaultFallbackModel, Supplier<String> fallbackModelSupplier) {
        this.primary = primary;
        this.fallback = fallback;
        this.defaultFallbackModel = defaultFallbackModel;
        this.fallbackModelSupplier = fallbackModelSupplier;
    }

    @Override
    public ChatResponse call(Prompt prompt) {
        try {
            return primary.call(prompt);
        } catch (RuntimeException e) {
            String fbModel = resolveFallbackModel();
            log.warn("主模型调用失败（{}），降级到 [{}] 重试一次", rootMessage(e), fbModel);
            return fallback.call(rewriteModel(prompt, fbModel));
        }
    }

    @Override
    public Flux<ChatResponse> stream(Prompt prompt) {
        // 流式的降级在错误信号处切到备用模型，前缀已发出的 chunk 由调用方拼接逻辑兜底
        // （refineStream 是累计拼接，切流后文本连续性可接受）
        return primary.stream(prompt)
                .onErrorResume(e -> {
                    String fbModel = resolveFallbackModel();
                    log.warn("主模型流式失败（{}），降级到 [{}] 重试一次", rootMessage(e), fbModel);
                    return fallback.stream(rewriteModel(prompt, fbModel));
                });
    }

    private String resolveFallbackModel() {
        String runtime = fallbackModelSupplier == null ? null : fallbackModelSupplier.get();
        return (runtime == null || runtime.isBlank()) ? defaultFallbackModel : runtime;
    }

    /**
     * 把 Prompt 的 options.model 改写成备用模型名。
     * 只处理 OpenAiChatOptions（本工程所有 per-request options 都是我们构造的）；
     * 无 options 时新建一份只带模型名的。
     */
    private Prompt rewriteModel(Prompt prompt, String fallbackModel) {
        ChatOptions origin = prompt.getOptions();
        OpenAiChatOptions.Builder builder = OpenAiChatOptions.builder().model(fallbackModel);
        if (origin instanceof OpenAiChatOptions o) {
            if (o.getTemperature() != null) {
                builder.temperature(o.getTemperature());
            }
        }
        return new Prompt(prompt.getInstructions(), builder.build());
    }

    private String rootMessage(Throwable e) {
        Throwable t = e;
        while (t.getCause() != null && t.getCause() != t) {
            t = t.getCause();
        }
        String msg = t.getMessage();
        return msg == null ? t.getClass().getSimpleName() : msg.substring(0, Math.min(160, msg.length()));
    }
}
