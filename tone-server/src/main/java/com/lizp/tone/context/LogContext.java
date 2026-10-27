package com.lizp.tone.context;

import com.alibaba.ttl.TransmittableThreadLocal;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 操作日志补充信息。
 *
 * <p>解决的问题：有些操作"作用在谁身上"从方法入参里看不出来。
 * 强制下线就是典型——入参只有一个会话主键 {@code tokenId}，
 * 日志里只会留下一个看不懂的数字，事后要追"到底踢了谁"只能翻会话表。
 * Service 层拿到真实记录后往这里塞几个字段，{@code LogAspect} 收尾时一并写进日志。
 *
 * <p>为什么不用返回值：日志是切面在 {@code finally} 里落的，
 * 改返回值会污染接口契约；而业务方法内部最清楚被操作对象是谁。
 *
 * <p>与 {@link UserContextHolder} 一样用 TTL：日志异步落库，普通 ThreadLocal
 * 在线程池里既传不过去，又会被复用线程带上一次请求的脏数据。
 *
 * <p>使用约束：{@code LogAspect} 落库时用 {@link #take()} 取走并清空；
 * 没有切面兜底的地方（定时任务等）用完必须自己 {@link #clear()}。
 */
public final class LogContext {

    private static final TransmittableThreadLocal<Map<String, Object>> CONTEXT =
            new TransmittableThreadLocal<>();

    private LogContext() {
    }

    /** 写入一条补充信息；key 用英文，日志里是给运维看的 JSON */
    public static void put(String key, Object value) {
        if (key == null || value == null) {
            return;
        }
        Map<String, Object> map = CONTEXT.get();
        if (map == null) {
            map = new LinkedHashMap<>();
            CONTEXT.set(map);
        }
        map.put(key, value);
    }

    /** 取走全部补充信息并清空（切面落库时用这个，避免线程复用串到下一条日志） */
    public static Map<String, Object> take() {
        Map<String, Object> map = CONTEXT.get();
        if (map != null) {
            CONTEXT.remove();
        }
        return map == null ? Map.of() : Map.copyOf(map);
    }

    public static void clear() {
        CONTEXT.remove();
    }
}
