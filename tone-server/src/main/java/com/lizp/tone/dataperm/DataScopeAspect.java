package com.lizp.tone.dataperm;

import cn.hutool.core.util.StrUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.Set;

/**
 * 数据权限切面：方法执行前把「当前用户的数据范围」放进线程上下文，
 * 由 {@link DataScopeHandler} 在 SQL 拼接时读取，方法结束后必须清理。
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class DataScopeAspect {

    @Around("@annotation(com.lizp.tone.dataperm.DataScope)")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        try {
            setup(joinPoint);
            return joinPoint.proceed();
        } finally {
            // finally 里清理：方法抛异常时也必须清掉，否则线程复用会串数据
            DataScopeContext.clear();
        }
    }

    private void setup(ProceedingJoinPoint joinPoint) {
        DataScope annotation = resolveAnnotation(joinPoint);
        if (annotation == null || annotation.ignore()) {
            return;
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof com.lizp.tone.security.LoginUser loginUser)) {
            return;
        }
        // 超级管理员不做数据过滤
        if (loginUser.isSuperAdmin()) {
            return;
        }
        String alias = annotation.alias();
        String deptColumn = StrUtil.isBlank(alias)
                ? annotation.deptField()
                : alias + "." + annotation.deptField();
        String userColumn = StrUtil.isBlank(alias)
                ? annotation.userField()
                : alias + "." + annotation.userField();

        Set<Long> deptIds = loginUser.getCustomDeptIds();
        DataScopeContext.set(new DataScopeContext.ScopeInfo(
                alias,
                deptColumn,
                userColumn,
                loginUser.getDataScope(),
                loginUser.getDeptId(),
                loginUser.getUserId(),
                deptIds));
    }

    private DataScope resolveAnnotation(ProceedingJoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        DataScope annotation = method.getAnnotation(DataScope.class);
        if (annotation != null) {
            return annotation;
        }
        try {
            // 注解标在接口方法上时的兜底：到目标类上再找一次
            Method targetMethod = joinPoint.getTarget().getClass()
                    .getMethod(method.getName(), method.getParameterTypes());
            return targetMethod.getAnnotation(DataScope.class);
        } catch (NoSuchMethodException e) {
            return null;
        }
    }
}
