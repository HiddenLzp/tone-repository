package com.lizp.tone.context;

import com.alibaba.ttl.TransmittableThreadLocal;
import com.lizp.tone.security.LoginUser;

/**
 * 当前用户上下文。
 *
 * <p>用 TransmittableThreadLocal 而不是普通 ThreadLocal 的原因：
 * 操作日志用 @Async 异步落库，定时任务、自定义线程池里也需要知道"当前是谁"。
 * 普通 ThreadLocal 在线程池场景下既传不过去，又会因为线程复用拿到上一个用户的信息（串号）。
 * TTL 能在提交任务时把上下文一并传递，配合 TtlExecutors 包装线程池即可。
 *
 * <p>使用约束：用完必须 {@link #clear()}，否则线程复用会残留脏数据。
 */
public final class UserContextHolder {

    private static final TransmittableThreadLocal<LoginUser> CONTEXT = new TransmittableThreadLocal<>();

    private UserContextHolder() {
    }

    public static void set(LoginUser user) {
        CONTEXT.set(user);
    }

    public static LoginUser get() {
        return CONTEXT.get();
    }

    public static Long getUserId() {
        LoginUser user = CONTEXT.get();
        return user == null ? null : user.getUserId();
    }

    public static String getUsername() {
        LoginUser user = CONTEXT.get();
        return user == null ? null : user.getUsername();
    }

    public static void clear() {
        CONTEXT.remove();
    }
}
