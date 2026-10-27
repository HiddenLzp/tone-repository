package com.lizp.tone.module.agent.llm;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.List;

/**
 * 开发联调用的 Mock 模型：无需 API Key。
 *
 * <p>根据 Prompt 里的标记词区分两种请求：
 * <ul>
 *   <li>检查请求（含 {@code ORIGINAL_TEXT}）：返回完整的「评分 + 问题 + 3 版本」JSON</li>
 *   <li>改写请求（含 {@code REFINE_INSTRUCTION}）：返回单个改写结果 JSON</li>
 * </ul>
 * 返回假 Usage，保证看板的 Token 统计链路也能在 Mock 下跑通。
 */
public class MockChatModel implements ChatModel {

    private static final String CHECK_JSON = """
            {
              "scores": {"politeness": 42, "professionalism": 55, "emotion": -30},
              "issues": [
                {"type": "politeness", "level": "high", "quote": "你到底什么时候给我答复",
                 "suggestion": "催促语气过重，建议改为询问进度并留出缓冲：'想了解一下这件事目前的进展'"},
                {"type": "clarity", "level": "mid", "quote": "再拖下去谁都别想好过",
                 "suggestion": "威胁式表达容易激化矛盾，建议改为陈述影响：'时间紧张的话可能会影响整体排期'"}
              ],
              "versions": [
                {"style": "委婉礼貌", "text": "你好，想跟进一下之前提的那件事，想了解一下目前的进展如何？如果近期方便的话，麻烦你有空时回复我一下，非常感谢！"},
                {"style": "专业得体", "text": "您好，关于上次沟通的事项，希望同步一下当前状态。考虑到整体排期安排，烦请您确认一下预计完成时间，如有困难也可以一并说明，我们一起看怎么协调。"},
                {"style": "温和亲近", "text": "嘿，上次说的事儿现在进展怎么样啦？我这边有点着急，你方便的时候跟我说一声就好，要是有卡住的地方咱们一起想办法~"}
              ]
            }
            """;

    /**
     * 微调 Mock 输出：纯文本（与真实 prompt 约定一致，见 buildRefineUserPrompt 的注释——
     * 微调流式要逐字渲染，不能带 JSON 外壳）。
     */
    private static final String REFINE_TEXT = "你好，想再跟进一下之前提的那件事。不着急，你看完消息后方便的时候回复我就好，先谢谢你啦！";

    @Override
    public ChatResponse call(Prompt prompt) {
        String text = prompt.getContents();
        String content = text.contains("REFINE_INSTRUCTION") ? REFINE_TEXT : CHECK_JSON;
        Generation generation = new Generation(new AssistantMessage(content),
                ChatGenerationMetadata.builder().finishReason("stop").build());
        ChatResponseMetadata metadata = ChatResponseMetadata.builder()
                .usage(new DefaultUsage(320, 180))
                .model("mock")
                .build();
        return new ChatResponse(List.of(generation), metadata);
    }

    /**
     * ChatModel 接口的 stream(Prompt) 默认实现是抛 UnsupportedOperationException
     * （"streaming is not supported"）。微调的流式接口走 chatModel.stream()，
     * 所以 Mock 必须覆盖：把整段响应包成单元素 Flux，语义上等价于"一次到位"的流。
     */
    @Override
    public reactor.core.publisher.Flux<ChatResponse> stream(Prompt prompt) {
        return reactor.core.publisher.Flux.just(call(prompt));
    }
}
