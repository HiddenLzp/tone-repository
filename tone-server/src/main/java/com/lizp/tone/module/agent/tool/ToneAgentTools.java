package com.lizp.tone.module.agent.tool;

import com.lizp.tone.module.agent.config.AgentProperties;
import com.lizp.tone.module.agent.prompt.AgentPrompts;
import com.lizp.tone.module.agent.service.SceneRuleService;
import com.lizp.tone.module.agent.service.SensitiveWordService;
import com.lizp.tone.module.agent.stream.AgentProgressListener;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.tool.annotation.Tool;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * ReAct 循环里 Agent 可调用的 5 个工具。
 *
 * <p>每个请求 new 一个实例（携带 scene/originalText 请求态 + 工具调用计数），
 * 通过 {@code ChatClient.prompt().tools(toolsInstance)} 传入，天然隔离并发请求。
 *
 * <p>analyze_tone / rewrite_text 的实现是内部一次结构化 LLM 调用（JSON 输出），
 * 外层 ReAct 循环只负责编排；check_scene_rules / detect_sensitive_words 是确定性本地逻辑。
 *
 * <p>进度事件：每个工具入口回调 {@link AgentProgressListener}。之所以选"工具被实际调用"
 * 作为进度切分点，而不是编排器猜测的阶段——真实模式下模型自主决定工具顺序，只有这里
 * 的事件才与实际执行严格一致；前端拿到的每个提示都对应真实发生的事。
 */
@Slf4j
public class ToneAgentTools {

    // chatModel
    private final ChatModel innerChatModel;
    // 敏感词服务
    private final SensitiveWordService sensitiveWordService;
    // 场景规则库
    private final SceneRuleService sceneRuleService;
    // 语气 Agent 配置
    private final AgentProperties agentProperties;
    /** 阶段进度监听器：SSE 传输时把阶段事件推给前端；同步接口传 noop */
    private final AgentProgressListener progressListener;
    // 工具调用计数：超限时直接返回 TOOL_BUDGET_EXCEEDED
    private final AtomicInteger toolCalls = new AtomicInteger();

    /** 请求态：编排器发起前设置 */
    @Getter
    private String scene;
    @Getter
    private String originalText;

    /** 工具运行的产物 */
    @Getter
    private volatile String analysisJson;
    @Getter
    private volatile String versionsJson;
    @Getter
    private volatile String savedFinalJson;

    /** 工具内部 LLM 调用的 token 累计（编排器读取后落库） */
    // 用 AtomicLong 原子累加，避免 volatile 字段 read-modify-write 竞态（同文件 toolCalls 也是此做法）
    private final AtomicLong promptTokens = new AtomicLong();
    private final AtomicLong completionTokens = new AtomicLong();

    public long getPromptTokens() {
        return promptTokens.get();
    }

    public long getCompletionTokens() {
        return completionTokens.get();
    }

    public ToneAgentTools(ChatModel innerChatModel,
                          SensitiveWordService sensitiveWordService,
                          SceneRuleService sceneRuleService,
                          AgentProperties agentProperties) {
        this(innerChatModel, sensitiveWordService, sceneRuleService, agentProperties,
                AgentProgressListener.noop());
    }

    public ToneAgentTools(ChatModel innerChatModel,
                          SensitiveWordService sensitiveWordService,
                          SceneRuleService sceneRuleService,
                          AgentProperties agentProperties,
                          AgentProgressListener progressListener) {
        this.innerChatModel = innerChatModel;
        this.sensitiveWordService = sensitiveWordService;
        this.sceneRuleService = sceneRuleService;
        this.agentProperties = agentProperties;
        this.progressListener = progressListener;
    }

    public void bind(String scene, String originalText) {
        this.scene = scene;
        this.originalText = originalText;
        this.toolCalls.set(0);
    }

    /** 已发生的工具调用次数（含超限被拒的），编排器据此计算执行轮数 */
    public int getToolCallCount() {
        return toolCalls.get();
    }

    private boolean overBudget() {
        return toolCalls.incrementAndGet() > agentProperties.getMaxRounds();
    }

    @Tool(name = "detect_sensitive_words", description = "检测原文是否包含敏感词，返回命中的词列表")
    public String detectSensitiveWords() {
        // 进度
        progressListener.onStage("sensitive", "正在校验敏感词…");
        if (overBudget()) {
            return "TOOL_BUDGET_EXCEEDED：已达工具调用上限，请直接输出最终 JSON";
        }
        List<String> hits = sensitiveWordService.findHits(originalText);
        return hits.isEmpty() ? "未命中敏感词" : "命中敏感词: " + String.join(", ", hits);
    }

    @Tool(name = "analyze_tone", description = "对原文做语气诊断，返回礼貌度/专业度/情感倾向评分与问题列表 JSON")
    public String analyzeTone() {
        progressListener.onStage("analyze", "正在诊断语气…");
        if (overBudget()) {
            return "TOOL_BUDGET_EXCEEDED：已达工具调用上限，请直接输出最终 JSON";
        }
        // 构建提示词
        String prompt = AgentPrompts.buildAnalyzePrompt(scene, originalText);
        String json = innerCall(prompt);
        this.analysisJson = json;
        return "语气诊断结果: " + json;
    }

    @Tool(name = "check_scene_rules", description = "获取当前沟通场景的专项规则清单")
    public String checkSceneRules() {
        progressListener.onStage("rules", "正在读取场景规则…");
        if (overBudget()) {
            return "TOOL_BUDGET_EXCEEDED：已达工具调用上限，请直接输出最终 JSON";
        }
        SceneRuleService.SceneRule rule = sceneRuleService.getRule(scene);
        String label = rule != null ? rule.label() : AgentPrompts.sceneLabel(scene);
        return "场景[" + label + "]规则:\n" + sceneRuleService.rulesText(scene);
    }

    @Tool(name = "rewrite_text", description = "按场景规则把原文改写成委婉礼貌/专业得体/温和亲近三个版本")
    public String rewriteText() {
        progressListener.onStage("rewrite", "正在生成 3 个改写版本…");
        if (overBudget()) {
            return "TOOL_BUDGET_EXCEEDED：已达工具调用上限，请直接输出最终 JSON";
        }
        String prompt = AgentPrompts.buildRewritePrompt(
                scene,
                sceneRuleService.rulesText(scene),
                originalText);
        String json = innerCall(prompt);
        this.versionsJson = json;
        return "改写结果: " + json;
    }

    @Tool(name = "save_version", description = "保存最终结果 JSON（scores/issues/versions 结构）")
    public String saveVersion(String finalJson) {
        progressListener.onStage("save", "正在保存结果…");
        if (overBudget()) {
            return "TOOL_BUDGET_EXCEEDED";
        }
        this.savedFinalJson = finalJson;
        return "已保存";
    }

    /** 工具内部的一次结构化 LLM 调用，累计 token 用量；模型/温度由 bean 内包装层统一注入 */
    private String innerCall(String prompt) {
        ChatResponse response = innerChatModel.call(new Prompt(prompt));
        // 获取 token 使用情况
        Usage usage = response.getMetadata().getUsage();

        // 提示词token 使用情况累加
        this.promptTokens.addAndGet(usage.getPromptTokens());
        // 完成token 使用情况累加
        this.completionTokens.addAndGet(usage.getCompletionTokens());
        // 获取输出文本
        Generation result = response.getResult();
        return result == null ? null : result.getOutput().getText();
    }
}
