package com.lizp.tone.module.agent.llm;

import com.lizp.tone.module.agent.service.AgentRuntimeConfigService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;
import reactor.core.publisher.Flux;

/**
 * 运行时模型选项注入：把「当前生效的模型/温度」（sys_config，管理台可在线改）
 * 以 <b>OpenAiChatOptions</b> 的形态塞进每个 Prompt 再委托底层模型。
 *
 * <p>为什么需要它（踩过的坑，别再绕）：ChatClient.prompt().options(builder) 与
 * Prompt.Builder.defaultOptions(...) 都会把自定义器收编成通用 {@code DefaultChatOptions}
 * （Spring AI 2.0 的 Prompt 层刻意厂商无关），而 OpenAiChatModel 对 prompt options
 * 是<b>强转</b> OpenAiChatOptions —— 两条路都会 ClassCastException。
 * 唯一稳妥的注入点是 ChatModel 调用层：这里我们完全掌控 options 的具体类型。
 *
 * <p>替换策略：无论传入 Prompt 有无 options（null / DefaultChatOptions /
 * 旧的 OpenAiChatOptions），一律替换为按运行时配置新建的 OpenAiChatOptions。
 * 当前所有调用方都不会在 prompt 上设置模型/温度以外的字段，整替无信息损失。
 */
@Slf4j
public class RuntimeOptionsChatModel implements ChatModel {

    private final ChatModel delegate;
    private final AgentRuntimeConfigService runtimeConfig;

    public RuntimeOptionsChatModel(ChatModel delegate, AgentRuntimeConfigService runtimeConfig) {
        this.delegate = delegate;
        this.runtimeConfig = runtimeConfig;
    }

    @Override
    public ChatResponse call(Prompt prompt) {
        return delegate.call(withRuntimeOptions(prompt));
    }

    @Override
    public Flux<ChatResponse> stream(Prompt prompt) {
        return delegate.stream(withRuntimeOptions(prompt));
    }

    private Prompt withRuntimeOptions(Prompt prompt) {
        ChatOptions origin = prompt.getOptions();
        if (origin instanceof OpenAiChatOptions o
                && runtimeConfig.model().equals(o.getModel())) {
            // 已经是运行时模型（如 FallbackChatModel 改写过的降级请求），不重复替换
            return prompt;
        }
        return new Prompt(prompt.getInstructions(), runtimeConfig.newOptions());
    }
}
