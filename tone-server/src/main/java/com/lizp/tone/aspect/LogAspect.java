package com.lizp.tone.aspect;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lizp.tone.context.LogContext;
import com.lizp.tone.module.monitor.OperationLogService;
import com.lizp.tone.module.monitor.entity.SysOperationLog;
import com.lizp.tone.security.LoginUser;
import com.lizp.tone.utils.AddressUtils;
import com.lizp.tone.utils.IpUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 操作日志切面。
 *
 * <p>记录：操作人、URL、IP、归属地、方法、入参、返回值、耗时、成功/失败与错误原因。
 *
 * <p>三个容易忽略的点：
 * <ol>
 *   <li>入参要做脱敏，否则密码、token 会明文进日志表；</li>
 *   <li>入参/返回值要截断，否则一个大 List 就能把字段撑爆；</li>
 *   <li>落库交给 {@code @Async}，不占用主流程耗时。</li>
 * </ol>
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class LogAspect {

    /** 需要脱敏的字段名（不区分大小写，子串匹配） */
    private static final Set<String> SENSITIVE_KEYS = Set.of(
            "password", "passwd", "pwd", "secret", "token", "captcha", "code", "authorization");

    private static final Pattern SENSITIVE_PATTERN = Pattern.compile(
            "\"([^\"]*(?:password|passwd|pwd|secret|token|captcha|authorization)[^\"]*)\"\\s*:\\s*\"[^\"]*\"",
            Pattern.CASE_INSENSITIVE);

    private static final int MAX_LENGTH = 2000;

    private final OperationLogService operationLogService;
    private final ObjectMapper objectMapper;

    @Around("@annotation(com.lizp.tone.aspect.Log)")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        Log annotation = method.getAnnotation(Log.class);

        SysOperationLog entity = new SysOperationLog();
        entity.setTitle(annotation.title());
        entity.setBusinessType(annotation.businessType().getValue());
        entity.setOperatorType(1);
        entity.setMethod(signature.getDeclaringTypeName() + "." + method.getName());
        entity.setCreateTime(LocalDateTime.now());

        HttpServletRequest request = currentRequest();
        if (request != null) {
            entity.setRequestMethod(request.getMethod());
            entity.setOperUrl(StrUtil.maxLength(request.getRequestURI(), 250));
            String ip = IpUtils.getIp(request);
            entity.setOperIp(ip);
            entity.setOperLocation(AddressUtils.getAddress(ip));
        }
        LoginUser currentUser = currentUser();
        if (currentUser != null) {
            entity.setOperName(currentUser.getUsername());
        }
        if (annotation.saveParams()) {
            entity.setOperParam(truncateSafe(mask(mergeParams(joinPoint.getArgs()))));
        }

        long start = System.currentTimeMillis();
        try {
            Object result = joinPoint.proceed();
            entity.setStatus(0);
            if (annotation.saveResult()) {
                entity.setJsonResult(truncateSafe(mask(toJson(result))));
            }
            return result;
        } catch (Throwable e) {
            entity.setStatus(1);
            entity.setErrorMsg(truncateSafe(e.getClass().getSimpleName() + ": " + e.getMessage()));
            throw e;
        } finally {
            entity.setCostTime(System.currentTimeMillis() - start);
            // 兜底清空：saveParams=false 时没人来取，线程复用会把这次的补充信息带到下一条日志
            LogContext.clear();
            // 异步落库：日志写入不该拖慢业务接口，也不该因为业务失败而丢失
            operationLogService.save(entity);
        }
    }

    private HttpServletRequest currentRequest() {
        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attributes == null ? null : attributes.getRequest();
    }

    private LoginUser currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof LoginUser loginUser) {
            return loginUser;
        }
        return com.lizp.tone.context.UserContextHolder.get();
    }

    /**
     * 入参 + 业务补充信息。
     *
     * <p>没有补充信息时完全沿用原来的序列化结果（老日志格式不变）；
     * 只有业务方法往 {@link com.lizp.tone.context.LogContext} 里塞了东西才拼成对象，
     * 例如强制下线：`{"args":12,"targetUser":"zhangsan","targetIp":"192.168.10.5"}`——
     * 光看入参那个 12 是查不出踢了谁的。
     */
    private String mergeParams(Object[] args) {
        Map<String, Object> extra = LogContext.take();
        if (extra.isEmpty()) {
            return toJson(args);
        }
        Map<String, Object> merged = new LinkedHashMap<>();
        merged.put("args", args.length == 1 ? args[0] : args);
        merged.putAll(extra);
        return toJson(merged);
    }

    private String toJson(Object obj) {
        if (obj == null) {
            return "";
        }
        try {
            // 数组本身序列化出来是 JSON 数组，取单个参数时直接序列化对象更直观
            Object target = obj instanceof Object[] args && args.length == 1 ? args[0] : obj;
            if (target instanceof Object[] args && args.length == 0) {
                return "";
            }
            return objectMapper.writeValueAsString(target);
        } catch (Exception e) {
            return String.valueOf(obj);
        }
    }

    /** 敏感字段打码：宁可多打码也不能把密码写进日志表 */
    private String mask(String json) {
        if (StrUtil.isBlank(json)) {
            return json;
        }
        return SENSITIVE_PATTERN.matcher(json).replaceAll(m -> {
            String matched = m.group();
            int colon = matched.lastIndexOf(':');
            return matched.substring(0, colon + 1) + "\"******\"";
        });
    }

    private String truncateSafe(String text) {
        if (text == null) {
            return null;
        }
        return StrUtil.maxLength(text, MAX_LENGTH);
    }
}
