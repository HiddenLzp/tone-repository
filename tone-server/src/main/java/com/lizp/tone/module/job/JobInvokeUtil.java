package com.lizp.tone.module.job;

import cn.hutool.core.util.StrUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 反射调用定时任务目标。
 *
 * <p>支持三种写法：
 * <pre>
 *   logCleanTask.cleanOperationLog()
 *   reportTask.generate(7)
 *   reportTask.generate('weekly', true)
 * </pre>
 *
 * <p>只支持字符串 / 数字 / 布尔三类参数：定时任务的参数通常来自数据库里的文本，
 * 支持太多类型只会让解析逻辑变得脆弱。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JobInvokeUtil {

    private static final Pattern TARGET_PATTERN =
            Pattern.compile("^([A-Za-z0-9_]+)\\.([A-Za-z0-9_]+)\\((.*)\\)$");

    private final ApplicationContext applicationContext;

    public void invoke(String invokeTarget) {
        if (StrUtil.isBlank(invokeTarget)) {
            throw new IllegalArgumentException("调用目标为空");
        }
        Matcher matcher = TARGET_PATTERN.matcher(invokeTarget.trim());
        if (!matcher.matches()) {
            throw new IllegalArgumentException("调用目标格式不正确，应为 bean.method(参数)：" + invokeTarget);
        }
        String beanName = matcher.group(1);
        String methodName = matcher.group(2);
        String paramText = matcher.group(3);

        Object bean;
        try {
            bean = applicationContext.getBean(beanName);
        } catch (Exception e) {
            throw new IllegalArgumentException("找不到目标 Bean：" + beanName);
        }
        List<Object> params = parseParams(paramText);
        Method method = findMethod(bean, methodName, params.size());
        if (method == null) {
            throw new IllegalArgumentException("找不到目标方法：" + methodName + "/" + params.size() + " 个参数");
        }
        try {
            method.setAccessible(true);
            method.invoke(bean, convertParams(method, params).toArray());
        } catch (Exception e) {
            // 反射会把业务异常包一层 InvocationTargetException，剥出来再抛，日志里才能看到真正原因
            Throwable cause = e.getCause() == null ? e : e.getCause();
            throw new RuntimeException("任务执行失败：" + cause.getMessage(), cause);
        }
    }

    private List<Object> parseParams(String paramText) {
        List<Object> params = new ArrayList<>();
        if (StrUtil.isBlank(paramText)) {
            return params;
        }
        for (String raw : paramText.split(",")) {
            String token = raw.trim().replace("'", "").replace("\"", "");
            if (token.isEmpty()) {
                continue;
            }
            params.add(token);
        }
        return params;
    }

    private Method findMethod(Object bean, String methodName, int paramCount) {
        for (Method method : bean.getClass().getMethods()) {
            if (method.getName().equals(methodName) && method.getParameterCount() == paramCount) {
                return method;
            }
        }
        return null;
    }

    /** 把字符串参数转换成方法签名要求的类型 */
    private List<Object> convertParams(Method method, List<Object> params) {
        Class<?>[] types = method.getParameterTypes();
        List<Object> converted = new ArrayList<>();
        for (int i = 0; i < types.length; i++) {
            Object value = params.get(i);
            Class<?> type = types[i];
            if (type == String.class) {
                converted.add(String.valueOf(value));
            } else if (type == int.class || type == Integer.class) {
                converted.add(Integer.parseInt(String.valueOf(value)));
            } else if (type == long.class || type == Long.class) {
                converted.add(Long.parseLong(String.valueOf(value)));
            } else if (type == boolean.class || type == Boolean.class) {
                converted.add(Boolean.parseBoolean(String.valueOf(value)));
            } else if (type == double.class || type == Double.class) {
                converted.add(Double.parseDouble(String.valueOf(value)));
            } else {
                throw new IllegalArgumentException("不支持的参数类型：" + type.getName());
            }
        }
        return converted;
    }
}
