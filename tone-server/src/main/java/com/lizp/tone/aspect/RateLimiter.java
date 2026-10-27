package com.lizp.tone.aspect;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 接口限流注解：基于 Redis 计数，在时间窗口内限制请求次数。
 *
 * <pre>{@code
 * // 同一 IP 60 秒内最多 10 次
 * @RateLimiter(time = 60, count = 10, limitType = LimitType.IP)
 * @PostMapping("/login")
 * public R<LoginVO> login(...) { ... }
 * }</pre>
 *
 * <p>超限后不是"等窗口自己走完"，而是进入一个**从被拦那一刻算起**的完整窗口冷却期：
 * 等待时间对用户是确定的（到点必定放行），冷却期内继续请求只计数、不延长。
 * 所以 {@code message} 里不要再写"请 1 分钟后再试"这类固定话术，秒数由切面按剩余时间拼。
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimiter {

    /** 限流key 前缀，默认用「类名.方法名」 */
    String key() default "";

    /** 时间窗口（秒） */
    long time() default 60;

    /** 窗口内允许的次数 */
    int count() default 100;

    /** 按什么维度限流 */
    LimitType limitType() default LimitType.IP;

    /** 提示语 */
    String message() default "操作过于频繁，请稍后再试";

    enum LimitType {
        /** 按客户端 IP */
        IP,
        /** 按登录用户 */
        USER,
        /** 全局（所有请求共享一个计数器） */
        GLOBAL
    }
}
