package com.lizp.tone.utils;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Servlet 工具类：在非 Controller 场景（Filter / AOP / 异常处理器）里拿请求与响应对象，
 * 以及直接往响应里写 JSON。
 */
public final class ServletUtils {

    private ServletUtils() {
    }

    public static HttpServletRequest getRequest() {
        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attributes == null ? null : attributes.getRequest();
    }

    public static HttpServletResponse getResponse() {
        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attributes == null ? null : attributes.getResponse();
    }

    /**
     * 直接输出 JSON。用于 Security 的 401/403 处理器——那里不走 MVC 的消息转换器，
     * 只能自己写响应。
     */
    public static void renderJson(HttpServletResponse response, int status, String json) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(json);
        response.getWriter().flush();
    }
}
