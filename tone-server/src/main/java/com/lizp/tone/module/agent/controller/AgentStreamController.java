package com.lizp.tone.module.agent.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lizp.tone.common.BaseException;
import com.lizp.tone.common.ErrorCode;
import com.lizp.tone.module.agent.dto.CheckRequest;
import com.lizp.tone.module.agent.dto.RefineRequest;
import com.lizp.tone.module.agent.service.AgentService;
import com.lizp.tone.module.agent.vo.CheckResultVO;
import com.lizp.tone.module.wxapi.auth.WxUserContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;

/**
 * 语气检查 Agent 的 SSE 流式接口（小程序端）。
 *
 * <p>为什么必须流式：真实大模型（如 qwen3.8-max）走完整 ReAct 循环单次要 60 秒以上，
 * 超过小程序 wx.request 的 60 秒上限；同步返回既会超时，也让用户盯着静止屏幕干等。
 * 流式协议（SSE over POST + chunked）：
 * <pre>
 *   event: stage   data: {"name":"analyze","message":"正在诊断语气…"}   ← 工具实际被调用时发出
 *   event: delta   data: {"text":"..."}                                ← 仅微调接口：模型逐 token 输出
 *   event: result  data: {完整业务结果 JSON}                             ← 落库后的最终产物
 *   event: error   data: {"code":31004,"message":"..."}                ← 业务/模型异常（含已退配额语义）
 *   event: complete data: {}                                           ← 流正常结束（客户端据此收尾）
 * </pre>
 * 小程序端用 wx.request({ enableChunked: true }) 的 onChunkReceived 消费（见 tone-uniapp utils/stream.js）。
 *
 * <p>鉴权：仍走 WxAuthInterceptor（/api/** 统一拦截），openId 在进入异步线程**前**取出——
 * WxUserContext 是普通 ThreadLocal，不会跟随线程池切换，必须在 HTTP 线程上捕获后以参数传递。
 */
@Slf4j
@Tag(name = "语气检查 Agent（流式）")
@RestController
@RequestMapping("/api/agent")
@RequiredArgsConstructor
public class AgentStreamController {

    /** SSE 超时（毫秒）：真实模型完整循环实测 ~65 秒，取 3 倍余量；超时由 onTimeout 兜底收尾 */
    private static final long SSE_TIMEOUT_MILLIS = 180_000L;

    private final AgentService agentService;
    private final ObjectMapper objectMapper;
    /** 专用线程池见 {@link com.lizp.tone.module.agent.config.AgentStreamExecutorConfig} 的注释：为什么不能复用 Tomcat 工作线程 */
    private final ExecutorService agentStreamExecutor;

    @PostMapping("/check/stream")
    @Operation(summary = "语气检查（SSE 流式）：stage 阶段事件 + result 最终结果")
    public SseEmitter checkStream(@Valid @RequestBody CheckRequest request) {
        // openId 必须在 HTTP 线程上捕获（见类注释），异步线程里 WxUserContext 已不可用
        String openId = WxUserContext.getOpenId();
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MILLIS);
        registerCompletionCallbacks(emitter);

        agentStreamExecutor.execute(() -> {
            try {
                // listener 把工具层的真实阶段事件转发成 SSE 的 stage 事件
                CheckResultVO result = agentService.check(openId, request, (stage, message) ->
                        send(emitter, "stage", Map.of("name", stage, "message", message)));
                send(emitter, "result", result);
                send(emitter, "complete", Map.of());
                emitter.complete();
            } catch (BaseException be) {
                // 业务异常（敏感词/配额/参数）：前端按 code 分支处理
                sendErrorAndComplete(emitter, be.getCode(), be.getMessage());
            } catch (Exception e) {
                log.error("流式检查执行失败 openId={}", openId, e);
                sendErrorAndComplete(emitter, ErrorCode.UNKNOWN.code(), "系统异常，请稍后重试");
            }
        });
        return emitter;
    }

    @PostMapping("/refine/stream")
    @Operation(summary = "微调（SSE 流式）：delta 逐字输出 + result 最终结果")
    public SseEmitter refineStream(@Valid @RequestBody RefineRequest request) {
        String openId = WxUserContext.getOpenId();
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MILLIS);
        registerCompletionCallbacks(emitter);

        agentStreamExecutor.execute(() -> {
            try {
                // delta 回调直接转发给前端，实现微调文本逐字渲染
                var result = agentService.refine(openId, request, piece ->
                        send(emitter, "delta", Map.of("text", piece)));
                send(emitter, "result", result);
                send(emitter, "complete", Map.of());
                emitter.complete();
            } catch (BaseException be) {
                sendErrorAndComplete(emitter, be.getCode(), be.getMessage());
            } catch (Exception e) {
                log.error("流式微调执行失败 openId={}", openId, e);
                sendErrorAndComplete(emitter, ErrorCode.UNKNOWN.code(), "系统异常，请稍后重试");
            }
        });
        return emitter;
    }

    /** 客户端中途离开 / 超时的兜底：不 complete 的话连接句柄会一直挂着 */
    private void registerCompletionCallbacks(SseEmitter emitter) {
        emitter.onTimeout(emitter::complete);
        emitter.onError(t -> log.debug("SSE 连接异常断开（多为用户离开页面），忽略"));
    }

    /** 业务失败统一出口：error 事件 + 正常收尾（SSE 里错误不是传输层错误，流仍要优雅关闭） */
    private void sendErrorAndComplete(SseEmitter emitter, int code, String message) {
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("code", code);
            body.put("message", message);
            send(emitter, "error", body);
            send(emitter, "complete", Map.of());
            emitter.complete();
        } catch (Exception e) {
            log.debug("SSE 错误事件发送失败（连接已断开），忽略");
        }
    }

    /**
     * 单个事件的发送。所有 send 都要 try/catch：用户中途离开页面时底层连接已断，
     * send 抛 IOException 属正常现象，但不能让它中断 Agent 的执行线程（Agent 还在跑，
     * 结果仍要落库）。
     */
    private void send(SseEmitter emitter, String event, Object data) {
        try {
            emitter.send(SseEmitter.event()
                    .name(event)
                    .data(data, MediaType.APPLICATION_JSON));
        } catch (Exception e) {
            log.debug("SSE 事件 [{}] 发送失败（连接已断开），忽略", event);
        }
    }
}
