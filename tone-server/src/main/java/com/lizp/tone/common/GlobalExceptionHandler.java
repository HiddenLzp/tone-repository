package com.lizp.tone.common;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;

/**
 * 全局异常处理：把各类异常翻译成 {@link R}。
 *
 * <p>设计要点：
 * <ol>
 *   <li>业务异常返回 HTTP 200 + 业务码，前端统一在响应拦截器里按 code 分支；</li>
 *   <li>参数校验失败把「字段 - 错误信息」一起返回，前端可直接定位到表单项；</li>
 *   <li>未知异常只返回通用文案，详细堆栈写日志，避免把内部信息暴露给前端。</li>
 * </ol>
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 业务异常 */
    @ExceptionHandler(BaseException.class)
    public R<Void> handleBaseException(BaseException e, HttpServletRequest request) {
        log.warn("业务异常 uri={} code={} msg={}", request.getRequestURI(), e.getCode(), e.getMessage());
        return R.fail(e.getCode(), e.getMessage());
    }

    /** @RequestBody + @Valid 校验失败 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public R<List<FieldErrorVO>> handleMethodArgumentNotValid(MethodArgumentNotValidException e,
                                                              HttpServletRequest request) {
        List<FieldErrorVO> errors = e.getBindingResult().getFieldErrors().stream()
                .map(err -> new FieldErrorVO(err.getField(), err.getDefaultMessage()))
                .toList();
        log.warn("参数校验失败 uri={} errors={}", request.getRequestURI(), errors);
        return new R<>(ErrorCode.PARAM_INVALID.code(), buildMessage(errors),
                errors, System.currentTimeMillis(), null);
    }

    /** @ModelAttribute / 表单绑定校验失败 */
    @ExceptionHandler(BindException.class)
    public R<List<FieldErrorVO>> handleBindException(BindException e, HttpServletRequest request) {
        List<FieldErrorVO> errors = e.getBindingResult().getFieldErrors().stream()
                .map(err -> new FieldErrorVO(err.getField(), err.getDefaultMessage()))
                .toList();
        log.warn("参数绑定失败 uri={} errors={}", request.getRequestURI(), errors);
        return new R<>(ErrorCode.PARAM_INVALID.code(), buildMessage(errors),
                errors, System.currentTimeMillis(), null);
    }

    /** @Validated 标注在参数上（非对象）时抛这个 */
    @ExceptionHandler(ConstraintViolationException.class)
    public R<List<FieldErrorVO>> handleConstraintViolation(ConstraintViolationException e,
                                                           HttpServletRequest request) {
        List<FieldErrorVO> errors = e.getConstraintViolations().stream()
                .map(this::toFieldError)
                .toList();
        log.warn("参数约束校验失败 uri={} errors={}", request.getRequestURI(), errors);
        return new R<>(ErrorCode.PARAM_INVALID.code(), buildMessage(errors),
                errors, System.currentTimeMillis(), null);
    }

    /** 缺少必填的请求参数 */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public R<Void> handleMissingParam(MissingServletRequestParameterException e, HttpServletRequest request) {
        log.warn("缺少必填参数 uri={} param={}", request.getRequestURI(), e.getParameterName());
        return R.fail(ErrorCode.PARAM_INVALID, "缺少必填参数：" + e.getParameterName());
    }

    /** 请求体无法解析（JSON 格式错误等） */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public R<Void> handleNotReadable(HttpMessageNotReadableException e, HttpServletRequest request) {
        log.warn("请求体解析失败 uri={} msg={}", request.getRequestURI(), e.getMessage());
        return R.fail(ErrorCode.PARAM_INVALID, "请求体格式错误");
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public R<Void> handleMethodNotSupported(HttpRequestMethodNotSupportedException e, HttpServletRequest request) {
        log.warn("请求方式不支持 uri={} method={}", request.getRequestURI(), e.getMethod());
        return R.fail(ErrorCode.METHOD_NOT_ALLOWED);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public R<Void> handleNotFound(NoResourceFoundException e, HttpServletRequest request) {
        log.warn("资源不存在 uri={}", request.getRequestURI());
        return R.fail(ErrorCode.NOT_FOUND);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public R<Void> handleMaxUploadSize(MaxUploadSizeExceededException e, HttpServletRequest request) {
        log.warn("文件超过大小限制 uri={}", request.getRequestURI());
        return R.fail(ErrorCode.FILE_TOO_LARGE);
    }

    /** 兜底：未知异常 */
    @ExceptionHandler(Exception.class)
    public R<Void> handleException(Exception e, HttpServletRequest request) {
        log.error("系统异常 uri={}", request.getRequestURI(), e);
        return R.fail(ErrorCode.UNKNOWN);
    }

    private FieldErrorVO toFieldError(ConstraintViolation<?> violation) {
        String path = String.valueOf(violation.getPropertyPath());
        // 形如 "login.username"，前端只需要最后一段
        int idx = path.lastIndexOf('.');
        String field = idx >= 0 ? path.substring(idx + 1) : path;
        return new FieldErrorVO(field, violation.getMessage());
    }

    private String buildMessage(List<FieldErrorVO> errors) {
        if (errors.isEmpty()) {
            return ErrorCode.PARAM_INVALID.message();
        }
        return errors.get(0).message();
    }

    /** 字段级错误信息 */
    public record FieldErrorVO(String field, String message) {
    }
}
