package com.lizp.tone.security.authz;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * 接口权限登记表：启动时扫描所有 Controller 方法上的 {@link RequiresPermissions}，
 * 建立「请求方法 + URL 模式 → 权限标识」的映射。
 *
 * <p>放在内存里而不是每次查库：鉴权在每次请求都要走，查库会成为热点。
 * 角色/菜单变更时调用 {@link #refresh()} 重建即可。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PermissionRegistry {

    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    private final RequestMappingHandlerMapping handlerMapping;

    /** 写时复制：鉴权线程无锁读取，刷新时整体替换 */
    private volatile List<PermRule> rules = Collections.emptyList();

    @EventListener(ApplicationReadyEvent.class)
    public void init() {
        refresh();
    }

    /** 角色权限变更后调用，重建映射 */
    public void refresh() {
        List<PermRule> newRules = new ArrayList<>();
        // 用局部列表收集，最后一次性替换，避免遍历过程中被并发读到半截状态
        handlerMapping.getHandlerMethods().forEach((info, handlerMethod) -> {
            RequiresPermissions annotation = resolveAnnotation(handlerMethod);
            if (annotation == null) {
                return;
            }
            Set<String> patterns = info.getPatternValues();
            if (patterns.isEmpty()) {
                patterns = Set.of("/**");
            }
            Set<RequestMethod> requestMethods = info.getMethodsCondition().getMethods();
            List<String> methods = requestMethods.isEmpty()
                    ? List.of("*")
                    : requestMethods.stream().map(Enum::name).toList();
            for (String pattern : patterns) {
                for (String method : methods) {
                    newRules.add(new PermRule(method, pattern, annotation.value(), annotation.logical()));
                }
            }
        });
        this.rules = List.copyOf(newRules);
        log.info("接口权限登记完成，共 {} 条规则", newRules.size());
    }

    /**
     * 匹配当前请求需要的权限标识。
     *
     * @return 需要的权限；返回 null 表示该接口没有标注权限要求
     */
    public PermRule match(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String method = request.getMethod();
        PermRule fallback = null;
        for (PermRule rule : rules) {
            if (!rule.matchesMethod(method)) {
                continue;
            }
            if (PATH_MATCHER.match(rule.pattern(), uri)) {
                // 精确方法优先；没有精确匹配时退而求其次用 "*" 规则
                if (!"*".equals(rule.method())) {
                    return rule;
                }
                fallback = rule;
            }
        }
        return fallback;
    }

    /** 优先取方法上的注解，其次取类上的（类上标注表示整个 Controller 都需要） */
    private RequiresPermissions resolveAnnotation(HandlerMethod handlerMethod) {
        Method method = handlerMethod.getMethod();
        RequiresPermissions annotation =
                AnnotatedElementUtils.findMergedAnnotation(method, RequiresPermissions.class);
        if (annotation == null) {
            annotation = AnnotatedElementUtils.findMergedAnnotation(
                    handlerMethod.getBeanType(), RequiresPermissions.class);
        }
        return annotation;
    }

    /**
     * @param method   HTTP 方法，* 表示任意
     * @param pattern  URL 模式，Ant 风格
     * @param perms    需要的权限标识
     * @param logical  多个权限之间的逻辑关系
     */
    public record PermRule(String method, String pattern, String[] perms,
                           RequiresPermissions.Logical logical) {

        public boolean matchesMethod(String httpMethod) {
            return "*".equals(method) || method.equalsIgnoreCase(httpMethod);
        }
    }
}
