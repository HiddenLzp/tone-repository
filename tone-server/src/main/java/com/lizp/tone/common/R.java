package com.lizp.tone.common;

import io.swagger.v3.oas.annotations.media.Schema;
import org.slf4j.MDC;

import java.io.Serializable;

/**
 * 统一响应体。
 *
 * <p>约定：HTTP 状态码与业务码分离。
 * <ul>
 *   <li>业务成功：{@code code = 0}，HTTP 200</li>
 *   <li>业务失败：{@code code = 1xxxx/2xxxx/3xxxx}，HTTP 仍为 200，前端按 code 分支处理</li>
 *   <li>认证/鉴权失败：HTTP 401/403，body 中带细分 code（见 {@link ErrorCode}），
 *       便于前端区分「token 过期需刷新」与「无权限」</li>
 * </ul>
 *
 * @param <T> 数据类型
 */
@Schema(description = "统一响应体")
public record R<T>(
        @Schema(description = "业务码，0 表示成功") int code,
        @Schema(description = "提示信息") String message,
        @Schema(description = "业务数据") T data,
        @Schema(description = "响应时间戳(毫秒)") long timestamp,
        @Schema(description = "链路追踪ID，便于日志排查") String traceId
) implements Serializable {

    private static final String TRACE_ID = "traceId";

    public static <T> R<T> ok() {
        return ok(null);
    }

    public static <T> R<T> ok(T data) {
        return ok(data, "操作成功");
    }

    public static <T> R<T> ok(T data, String message) {
        return new R<>(ErrorCode.SUCCESS.code(), message, data, System.currentTimeMillis(), MDC.get(TRACE_ID));
    }

    public static <T> R<T> fail(String message) {
        return fail(ErrorCode.ERROR, message);
    }

    public static <T> R<T> fail(ErrorCode errorCode) {
        return fail(errorCode, errorCode.message());
    }

    public static <T> R<T> fail(ErrorCode errorCode, String message) {
        return new R<>(errorCode.code(), message, null, System.currentTimeMillis(), MDC.get(TRACE_ID));
    }

    public static <T> R<T> fail(int code, String message) {
        return new R<>(code, message, null, System.currentTimeMillis(), MDC.get(TRACE_ID));
    }

    /** 业务是否成功 */
    public boolean isSuccess() {
        return code == ErrorCode.SUCCESS.code();
    }
}
