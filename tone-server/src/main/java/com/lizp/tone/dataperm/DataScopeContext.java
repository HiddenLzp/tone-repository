package com.lizp.tone.dataperm;

import com.alibaba.ttl.TransmittableThreadLocal;

import java.util.Set;

/**
 * 数据权限上下文（线程隔离）。
 *
 * <p>由 {@code DataScopeAspect} 在方法执行前写入、执行后清理。
 * 用 TTL 是为了让 @Async、定时任务里也能继承到当前用户的数据范围。
 */
public final class DataScopeContext {

    private static final TransmittableThreadLocal<ScopeInfo> CONTEXT = new TransmittableThreadLocal<>();

    private DataScopeContext() {
    }

    public static void set(ScopeInfo info) {
        CONTEXT.set(info);
    }

    public static ScopeInfo get() {
        return CONTEXT.get();
    }

    public static void clear() {
        CONTEXT.remove();
    }

    /**
     * 数据范围描述。
     *
     * @param tableAlias 需要被过滤的表别名（联表时填主表别名，避免字段歧义）
     * @param deptColumn 部门字段（含别名），如 u.dept_id
     * @param userColumn 创建人字段（含别名），如 u.create_by
     * @param scopeType  1全部 2自定义 3本部门 4本部门及以下 5仅本人
     * @param deptId     当前用户部门ID
     * @param userId     当前用户ID
     * @param deptIds    允许访问的部门ID集合（scopeType=2 或 4 时使用）
     */
    public record ScopeInfo(
            String tableAlias,
            String deptColumn,
            String userColumn,
            Integer scopeType,
            Long deptId,
            Long userId,
            Set<Long> deptIds
    ) {
    }
}
