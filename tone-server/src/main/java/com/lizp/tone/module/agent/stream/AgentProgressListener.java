package com.lizp.tone.module.agent.stream;

/**
 * Agent 执行过程的阶段进度监听器。
 *
 * <p>为什么用监听器而不是返回 Flux：整个 Agent 循环是「模型 → 工具 → 模型」的多轮阻塞式
 * 往返（单轮工具内部还有二次 LLM 调用），最自然的进度切分点是"工具被实际调用"——
 * 每个工具开始执行时回调一次，前端就能拿到真实的阶段事件（而不是前端定时器伪造的假提示）。
 *
 * <p>传输层不感知：同步接口传 {@link #noop()}，SSE 接口传"往 SseEmitter 写 stage 事件"的实现，
 * Orchestrator / Tools 完全不知道自己跑在哪种传输上。
 */
@FunctionalInterface
public interface AgentProgressListener {

    /**
     * 一个阶段开始。
     *
     * @param stage   阶段英文标识（sensitive/analyze/rules/rewrite/save），前端可据此做图标或文案映射
     * @param message 展示给用户的中文提示，如"正在诊断语气…"；调用方保证已含省略号
     */
    void onStage(String stage, String message);

    /** 什么都不做的监听器：同步接口 / 单元测试用，保持调用方代码零分支 */
    static AgentProgressListener noop() {
        return (stage, message) -> {
        };
    }
}
