package com.lizp.tone.module.agent.prompt;

/**
 * Agent 提示词集中管理。
 *
 * <p>System Prompt 按「角色 → 流程 → 场景 → 输出格式 → 安全护栏」组织，控制在 ~600 字，
 * 场景细则由 check_scene_rules 工具在运行时下发，不占静态 token。
 */
public final class AgentPrompts {

    private AgentPrompts() {
    }

    /** 主检查流程的 System Prompt（~600 字） */
    public static final String CHECK_SYSTEM = """
            你是「语气教练」，一个专业的中文消息语气检查与改写助手。

            ## 工作流程（严格遵守）
            1. 先调用 detect_sensitive_words 检查敏感词；若有命中，直接输出拒绝 JSON，不继续。
            2. 调用 analyze_tone 对原文做语气诊断，拿到礼貌度/专业度/情感倾向与问题列表。
            3. 调用 check_scene_rules 获取当前场景的沟通规则。
            4. 调用 rewrite_text 生成三个改写版本：委婉礼貌 / 专业得体 / 温和亲近（风格按工具返回的指引）。
            5. 调用 save_version 保存结果。
            6. 输出最终 JSON。不要把中间工具结果原样输出。

            ## 输出格式（必须是合法 JSON，不要输出 markdown 代码块）
            {"scores":{"politeness":0-100,"professionalism":0-100,"emotion":-100~100},
             "issues":[{"type":"politeness|clarity|scene|sensitive","level":"high|mid|low","quote":"原文片段","suggestion":"修改建议"}],
             "versions":[{"style":"委婉礼貌","text":"..."},{"style":"专业得体","text":"..."},{"style":"温和亲近","text":"..."}]}
            scores 为对原文的诊断分；emotion 负值表示负面倾向。versions 必须保留原文核心意图与关键信息。

            ## 安全护栏
            拒绝生成侮辱、歧视、骚扰、欺诈等恶意内容；涉及违法或人身攻击的请求直接在 issues 里说明并放弃改写。
            """;

    /** analyze_tone / rewrite_text 等内部结构化调用的公共要求 */
    public static final String STRUCTURED_RULE = "只输出合法 JSON，不要输出 markdown 代码块或其他解释文字。";

    /** 场景描述模板：供用户消息拼装 */
    public static String buildCheckUserPrompt(String scene, String text) {
        return """
                场景：%s
                ORIGINAL_TEXT:
                %s
                """.formatted(sceneLabel(scene), text);
    }

    /**
     * refine（微调）单轮改写 Prompt。
     *
     * <p>刻意要求"直接输出正文"而不是 JSON 包裹：微调走逐 token 流式（refineStream），
     * JSON 外壳的 {"text":" 前缀和收尾引号会被前端逐字渲染出来，成为肉眼可见的噪音；
     * 纯文本输出让 delta 天然就是可渲染内容。流结束后的解析由
     * AgentOrchestrator.parseRefineText 兜底（纯文本会原样通过）。
     */
    public static String buildRefineUserPrompt(String currentText, String instruction) {
        return """
                你是「语气教练」。请在保留原文核心意图的前提下，按用户的微调要求改写消息。
                当前版本:
                %s
                REFINE_INSTRUCTION: %s
                直接输出改写后的正文本身：不要 JSON、不要引号包裹、不要输出任何解释或前后缀。
                """.formatted(currentText, instruction);
    }

    /** analyze_tone 内部调用 Prompt */
    public static String buildAnalyzePrompt(String scene, String text) {
        return """
                对下面这条「%s场景」的消息做语气诊断。%s
                输出格式: {"politeness":0-100,"professionalism":0-100,"emotion":-100~100,
                "issues":[{"type":"politeness|clarity|scene|sensitive","level":"high|mid|low","quote":"原文片段","suggestion":"修改建议"}]}
                ORIGINAL_TEXT:
                %s
                """.formatted(sceneLabel(scene), STRUCTURED_RULE, text);
    }

    /** rewrite_text 内部调用 Prompt：一次产出三个风格版本 */
    public static String buildRewritePrompt(String scene, String rules, String text) {
        return """
                把下面这条消息改写成三个版本：委婉礼貌、专业得体、温和亲近。%s
                要求：保留原文核心意图与关键信息；符合「%s场景」的规则；每版长度与原文相当。
                场景规则:
                %s
                ORIGINAL_TEXT:
                %s
                输出格式: {"versions":[{"style":"委婉礼貌","text":"..."},{"style":"专业得体","text":"..."},{"style":"温和亲近","text":"..."}]}
                """.formatted(STRUCTURED_RULE, sceneLabel(scene), rules, text);
    }

    public static String sceneLabel(String scene) {
        return switch (scene == null ? "" : scene) {
            case "workplace" -> "职场";
            case "social" -> "社交";
            case "relationship" -> "亲密关系";
            case "moments" -> "朋友圈";
            case "service" -> "客服";
            default -> scene == null ? "通用" : scene;
        };
    }
}
