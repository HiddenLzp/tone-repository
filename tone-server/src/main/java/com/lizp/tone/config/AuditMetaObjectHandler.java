package com.lizp.tone.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.lizp.tone.context.UserContextHolder;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 审计字段自动填充：create_time / update_time / create_by / update_by。
 *
 * <p>取值优先级：SecurityContext（HTTP 请求的正常路径）→ UserContextHolder
 * （@Async、定时任务、MQ 消费等没有 SecurityContext 的场景）。
 *
 * <p>{@code strictXxxFill} 只在字段为空时才填充，因此不会覆盖调用方显式传入的值。
 */
@Slf4j
@Component
public class AuditMetaObjectHandler implements MetaObjectHandler {

    @Override
    public void insertFill(MetaObject metaObject) {
        LocalDateTime now = LocalDateTime.now();
        Long userId = currentUserId();
        this.strictInsertFill(metaObject, "createTime", LocalDateTime.class, now);
        this.strictInsertFill(metaObject, "updateTime", LocalDateTime.class, now);
        if (userId != null) {
            this.strictInsertFill(metaObject, "createBy", Long.class, userId);
            this.strictInsertFill(metaObject, "updateBy", Long.class, userId);
        }
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        Long userId = currentUserId();
        this.strictUpdateFill(metaObject, "updateTime", LocalDateTime.class, LocalDateTime.now());
        if (userId != null) {
            this.strictUpdateFill(metaObject, "updateBy", Long.class, userId);
        }
    }

    private Long currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof com.lizp.tone.security.LoginUser loginUser) {
            return loginUser.getUserId();
        }
        return UserContextHolder.getUserId();
    }

    /** 保留：供子类扩展时判断字段是否已存在 */
    protected boolean hasField(MetaObject metaObject, String field) {
        return Objects.nonNull(metaObject) && metaObject.hasSetter(field);
    }
}
