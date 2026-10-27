package com.lizp.tone.security.authz;

import com.lizp.tone.config.BaseProperties;
import com.lizp.tone.security.LoginUser;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

/**
 * 动态接口鉴权：Spring Security 7 的扩展点（取代 Security 5 时代的
 * FilterInvocationSecurityMetadataSource）。
 *
 * <p>判定顺序：
 * <ol>
 *   <li>接口没登记权限要求 → 按 {@code tone.security.deny-unregistered-perm} 决定放行还是拒绝</li>
 *   <li>超级管理员 → 直接放行</li>
 *   <li>用户拥有的权限标识包含接口所需标识 → 放行，否则拒绝</li>
 * </ol>
 *
 * <p>用户的权限标识来自「角色 → 资源」表，登录时一次性载入 LoginUser，
 * 因此改角色权限后需要重新登录（或调用 refresh 重建会话）才生效。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DynamicAuthorizationManager
        implements AuthorizationManager<RequestAuthorizationContext> {

    private static final AuthorizationDecision GRANTED = new AuthorizationDecision(true);
    private static final AuthorizationDecision DENIED = new AuthorizationDecision(false);

    private final PermissionRegistry permissionRegistry;
    private final BaseProperties properties;

    @Override
    public AuthorizationDecision authorize(Supplier<? extends Authentication> authentication,
                                           RequestAuthorizationContext context) {
        HttpServletRequest request = context.getRequest();
        PermissionRegistry.PermRule rule = permissionRegistry.match(request);
        if (rule == null) {
            // 未登记要求的接口：默认放行，方便脚手架阶段快速开发；
            // 生产可通过配置改成默认拒绝，避免新接口漏加权限注解
            return properties.getSecurity().isDenyUnregisteredPerm() ? DENIED : GRANTED;
        }

        Authentication auth = authentication.get();
        if (auth == null || !auth.isAuthenticated()
                || !(auth.getPrincipal() instanceof LoginUser loginUser)) {
            return DENIED;
        }
        if (loginUser.isSuperAdmin()) {
            return GRANTED;
        }
        boolean passed = rule.logical() == RequiresPermissions.Logical.AND
                ? hasAllPerms(loginUser, rule.perms())
                : hasAnyPerm(loginUser, rule.perms());
        if (!passed) {
            log.debug("权限不足 user={} uri={} required={}",
                    loginUser.getUsername(), request.getRequestURI(), String.join(",", rule.perms()));
        }
        return passed ? GRANTED : DENIED;
    }

    private boolean hasAnyPerm(LoginUser user, String[] perms) {
        for (String perm : perms) {
            if (user.hasPerm(perm)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasAllPerms(LoginUser user, String[] perms) {
        for (String perm : perms) {
            if (!user.hasPerm(perm)) {
                return false;
            }
        }
        return true;
    }
}
