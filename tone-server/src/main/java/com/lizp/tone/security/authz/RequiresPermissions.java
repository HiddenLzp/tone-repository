package com.lizp.tone.security.authz;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 接口级权限标识：标注在 Controller 方法上，由 {@link DynamicAuthorizationManager} 在鉴权时读取。
 *
 * <p>为什么不用 {@code @PreAuthorize("hasAuthority('system:user:add')")}：
 * 权限标识写死在代码里，改权限就得改代码重新发版。这里只声明"这个接口需要哪个权限标识"，
 * 具体哪个角色拥有它，交给角色-资源表在运行时配置。
 */
@Documented
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequiresPermissions {

    /** 需要的权限标识，满足任意一个即可放行 */
    String[] value();

    /** 逻辑关系：AND 表示全部满足，OR 表示任一满足（默认） */
    Logical logical() default Logical.OR;

    enum Logical {
        AND, OR
    }
}
