package com.lizp.tone.aspect;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 操作日志注解：标注在需要留痕的 Controller / Service 方法上。
 *
 * <pre>{@code
 * @Log(title = "用户管理", businessType = BusinessType.INSERT)
 * @PostMapping
 * public R<Void> add(@RequestBody @Valid UserCreateRequest request) { ... }
 * }</pre>
 */
@Documented
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface Log {

    /** 模块标题 */
    String title() default "";

    /** 业务类型 */
    BusinessType businessType() default BusinessType.OTHER;

    /** 是否记录请求参数（含敏感信息的接口可关掉） */
    boolean saveParams() default true;

    /** 是否记录返回结果（返回体很大时可关掉） */
    boolean saveResult() default true;
}
