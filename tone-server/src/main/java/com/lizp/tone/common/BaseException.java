package com.lizp.tone.common;

import lombok.Getter;

/**
 * 业务异常：业务代码中主动抛出，由 {@link GlobalExceptionHandler} 转成统一响应体。
 *
 * <p>与直接用 {@code RuntimeException} 的区别：携带 {@link ErrorCode}，
 * 前端可以拿到稳定的机器可读码做分支处理，而不是靠文案匹配。
 */
@Getter
public class BaseException extends RuntimeException {

    private final int code;

    public BaseException(ErrorCode errorCode) {
        super(errorCode.message());
        this.code = errorCode.code();
    }

    public BaseException(ErrorCode errorCode, String message) {
        super(message);
        this.code = errorCode.code();
    }

    public BaseException(int code, String message) {
        super(message);
        this.code = code;
    }

    public BaseException(ErrorCode errorCode, Throwable cause) {
        super(errorCode.message(), cause);
        this.code = errorCode.code();
    }
}
