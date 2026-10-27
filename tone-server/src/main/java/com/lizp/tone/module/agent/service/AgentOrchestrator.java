package com.lizp.tone.module.agent.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lizp.tone.common.BaseException;
import com.lizp.tone.common.ErrorCode;
import com.lizp.tone.module.agent.config.AgentProperties;
import com.lizp.tone.module.agent.prompt.AgentPrompts;
import com.lizp.tone.module.agent.service.SceneRuleService;
import com.lizp.tone.module.agent.service.SensitiveWordService;
import com.lizp.tone.module.agent.tool.ToneAgentTools;
import com.lizp.tone.module.agent.stream.AgentProgressListener;
import com.lizp.tone.module.agent.vo.IssueVO;
import com.lizp.tone.module.agent.vo.ScoreVO;
import com.lizp.tone.module.agent.vo.VersionVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * ReAct 编排器：组装 System Prompt + 工具，驱动 ChatClient 的工具调用循环，
 * 解析最终 JSON 并做兜底（save_version 工具产物优先，正文解析次之，畸形输出再兜底）。
 */
@Slf4j
@Service
public class AgentOrchestrator {

    private final ChatModel chatModel;
    private final AgentProperties agentProperties;
    private final SensitiveWordService sensitiveWordService;
    private final SceneRuleService sceneRuleService;
    private final ObjectMapper objectMapper;
    /** 运行时配置：模型/温度从 sys_config 走（管理台可在线切换），per-request options 覆盖 bean 默认 */
    private final AgentRuntimeConfigService runtimeConfig;
    /** ChatClient 线程安全、无状态，构建一次复用；按请求变化的东西一律走 .prompt() 链 */
    private final ChatClient chatClient;

    public AgentOrchestrator(ChatModel chatModel, AgentProperties agentProperties,
                             SensitiveWordService sensitiveWordService, SceneRuleService sceneRuleService,
                             ObjectMapper objectMapper, AgentRuntimeConfigService runtimeConfig) {
        this.chatModel = chatModel;
        this.agentProperties = agentProperties;
        this.sensitiveWordService = sensitiveWordService;
        this.sceneRuleService = sceneRuleService;
        this.objectMapper = objectMapper;
        this.runtimeConfig = runtimeConfig;
        this.chatClient = ChatClient.builder(chatModel).build();
    }

    /** 一次完整 Agent 运行的产物 */
    public record AgentRunResult(ScoreVO scores, List<IssueVO> issues, List<VersionVO> versions,
                                 int rounds, long promptTokens, long completionTokens) {
    }

    /** 同步入口：无进度监听的便捷重载（管理台 / 单元测试 / 旧调用方） */
    public AgentRunResult run(String scene, String text) {
        return run(scene, text, AgentProgressListener.noop());
    }

    /**
     * 带进度监听的完整 ReAct 运行。
     *
     * <p>流式方案说明：最终产物是结构化 JSON（评分/问题/三版本），逐 token 推给前端无法
     * 增量渲染，所以 check 的"流式"是**阶段流**——事件源是工具被实际调用（见
     * {@link ToneAgentTools}），每个事件都对应真实发生的事，而非前端定时器伪造的提示。
     * ChatClient 负责工具调用的多轮回环，本方法只负责组装与结果解析。
     */
    public AgentRunResult run(String scene, String text, AgentProgressListener listener) {
        ToneAgentTools tools = new ToneAgentTools(chatModel, sensitiveWordService, sceneRuleService,
                agentProperties, listener);
        tools.bind(scene, text);
        // 循环开始前先发一个阶段事件：真实模式下首个工具被模型调用前有一段"思考"延迟，
        // 不发这条的话前端会长时间停在连接状态
        listener.onStage("start", "开始分析原文…");

        ChatResponse response = chatClient.prompt()
                .system(AgentPrompts.CHECK_SYSTEM)
                .user(AgentPrompts.buildCheckUserPrompt(scene, text))
                // 注意：这里刻意不传 .options(...) —— ChatClient 会把自定义器收编成
                // DefaultChatOptions，OpenAiChatModel 强转会 CCE（见 RuntimeOptionsChatModel 注释）；
                // 模型/温度的运行时注入由 chatModel bean 内的包装层完成
                .tools(tools)
                .call()
                .chatResponse();

        // 计算总token数
        long promptTokens = tools.getPromptTokens();
        long completionTokens = tools.getCompletionTokens();
        if (response != null) {
            Usage usage = response.getMetadata().getUsage();
            promptTokens += usage.getPromptTokens();
            completionTokens += usage.getCompletionTokens();
        }

        // save_version 工具的产物优先（LLM 自己确认过的最终结果），否则解析正文
        String finalJson = tools.getSavedFinalJson() != null
                ? tools.getSavedFinalJson()
                : extractJson(response != null && response.getResult() != null
                        ? response.getResult().getOutput().getText() : "");
        if (finalJson == null) {
            throw new BaseException(ErrorCode.LLM_RESPONSE_INVALID);
        }

        int rounds = Math.min(tools.getToolCallCount() + 1, agentProperties.getMaxRounds() + 1);
        ParsedResult parsed = parseResult(finalJson);
        return new AgentRunResult(parsed.scores(), parsed.issues(), parsed.versions(),
                rounds, promptTokens, completionTokens);
    }

    /** 微调（refine）：单轮结构化改写，不走完整 ReAct 循环 */
    public record RefineRun(String text, long promptTokens, long completionTokens) {
    }

    /**
     * 微调的流式实现：走 {@code chatModel.stream()} 逐 chunk 推给 onDelta，
     * 前端微调气泡可逐字渲染（对 qwen3.8-max 这类大杯模型，单次改写也要 20~40 秒，
     * 逐字出的体验差别是决定性的）。
     *
     * <p>模型输出是 {@code {"text":"..."}} 包裹的 JSON，这里**原样**返回全部累计文本
     * （含 JSON 外壳），由 {@link #parseRefineText} 统一解析——逐 token 阶段没法保证
     * 拿到的是合法 JSON 片段，解析必须等流结束。
     *
     * @param onDelta 每个 chunk 的纯文本增量回调；传 null 等价于不关心增量（同步语义）
     */
    public RefineRun refineStream(String currentText, String instruction,
                                  java.util.function.Consumer<String> onDelta) {
        java.util.function.Consumer<String> sink = onDelta == null ? s -> {
        } : onDelta;
        StringBuilder accumulated = new StringBuilder();
        long[] tokens = new long[2];
        // per-request options 由 chatModel bean 内的 RuntimeOptionsChatModel 注入（含流式）
        chatModel.stream(new org.springframework.ai.chat.prompt.Prompt(
                        AgentPrompts.buildRefineUserPrompt(currentText, instruction)))
                // 阻塞消费：本方法跑在 SSE 的异步工作线程里，逐 chunk 回调后再阻塞等下一条
                // 正是我们要的语义；Usage 若厂商只在末帧返回则取末帧，逐帧相加亦可
                .doOnNext(response -> {
                    if (response.getResult() != null && response.getResult().getOutput() != null) {
                        String piece = response.getResult().getOutput().getText();
                        if (piece != null && !piece.isEmpty()) {
                            accumulated.append(piece);
                            sink.accept(piece);
                        }
                    }
                    if (response.getMetadata() != null && response.getMetadata().getUsage() != null) {
                        Usage usage = response.getMetadata().getUsage();
                        if (usage.getPromptTokens() != null) {
                            tokens[0] = usage.getPromptTokens();
                        }
                        if (usage.getCompletionTokens() != null) {
                            tokens[1] = usage.getCompletionTokens();
                        }
                    }
                })
                .blockLast(Duration.ofSeconds(180));
        return new RefineRun(accumulated.toString(), tokens[0], tokens[1]);
    }

    /**
     * 解析微调输出：剥 JSON 外壳取 "text" 字段；模型没按格式输出时把正文当改写结果兜底。
     * 抽成静态方法是因为解析必须发生在流结束之后（见 {@link #refineStream} 的注释）。
     */
    public String parseRefineText(String content) {
        if (content == null || content.isBlank()) {
            return "";
        }
        String json = extractJson(content);
        if (json == null) {
            return content.trim();
        }
        try {
            JsonNode node = objectMapper.readTree(json);
            return node.path("text").asText(content.trim());
        } catch (Exception e) {
            return content.trim();
        }
    }

    /** 从模型输出里抠出 JSON（剥 markdown 围栏、取首尾大括号） */
    private String extractJson(String content) {
        if (content == null || content.isBlank()) {
            return null;
        }
        String cleaned = content.trim();
        if (cleaned.startsWith("```")) {
            cleaned = cleaned.replaceAll("^```(json)?\\s*", "").replaceAll("```\\s*$", "").trim();
        }
        int start = cleaned.indexOf('{');
        int end = cleaned.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return null;
        }
        return cleaned.substring(start, end + 1);
    }

    private record ParsedResult(ScoreVO scores, List<IssueVO> issues, List<VersionVO> versions) {
    }

    private ParsedResult parseResult(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode s = root.path("scores");
            ScoreVO scores = new ScoreVO(
                    clamp(s.path("politeness").asInt(50), 0, 100),
                    clamp(s.path("professionalism").asInt(50), 0, 100),
                    clamp(s.path("emotion").asInt(0), -100, 100));

            List<IssueVO> issues = new ArrayList<>();
            for (JsonNode n : root.path("issues")) {
                issues.add(new IssueVO(
                        n.path("type").asText("clarity"),
                        n.path("level").asText("mid"),
                        n.path("quote").asText(""),
                        n.path("suggestion").asText("")));
            }

            List<VersionVO> versions = new ArrayList<>();
            for (JsonNode n : root.path("versions")) {
                versions.add(new VersionVO(n.path("style").asText(""), n.path("text").asText("")));
            }
            if (versions.isEmpty()) {
                throw new BaseException(ErrorCode.LLM_RESPONSE_INVALID);
            }
            return new ParsedResult(scores, issues, versions);
        } catch (BaseException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Agent 结果解析失败: {}", json, e);
            throw new BaseException(ErrorCode.LLM_RESPONSE_INVALID);
        }
    }

    private int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }
}
