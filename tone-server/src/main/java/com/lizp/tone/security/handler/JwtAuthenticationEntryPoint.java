package com.lizp.tone.security.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lizp.tone.common.ErrorCode;
import com.lizp.tone.common.R;
import com.lizp.tone.utils.ServletUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

/**
 * 未认证处理器：请求需要登录但没有有效身份时走这里。
 *
 * <p>输出 HTTP 401 + 业务码，前端据此判断是"跳登录页"还是"刷新 token"。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) {
        log.debug("未认证访问 uri={} msg={}", request.getRequestURI(), authException.getMessage());
        try {
            ServletUtils.renderJson(response, HttpStatus.UNAUTHORIZED.value(),
                    objectMapper.writeValueAsString(R.fail(ErrorCode.UNAUTHORIZED)));
        } catch (Exception e) {
            log.error("写入未认证响应失败", e);
        }
    }
}
