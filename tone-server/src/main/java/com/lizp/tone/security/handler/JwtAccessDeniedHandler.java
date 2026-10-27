package com.lizp.tone.security.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lizp.tone.common.ErrorCode;
import com.lizp.tone.common.R;
import com.lizp.tone.security.LoginUser;
import com.lizp.tone.utils.ServletUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

/** 无权限处理器：已登录但权限不足时返回 403。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    @Override
    public void handle(HttpServletRequest request,
                       HttpServletResponse response,
                       AccessDeniedException accessDeniedException) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        ErrorCode errorCode = ErrorCode.FORBIDDEN;
        // 强制改密场景下给出专门的码，前端据此跳转改密页而不是弹"无权限"
        if (authentication != null && authentication.getPrincipal() instanceof LoginUser loginUser
                && Boolean.TRUE.equals(loginUser.getMustChangePassword())) {
            errorCode = ErrorCode.PASSWORD_MUST_CHANGE;
        }
        log.debug("无权限访问 uri={} user={}", request.getRequestURI(),
                authentication == null ? "anonymous" : authentication.getName());
        try {
            ServletUtils.renderJson(response, HttpStatus.FORBIDDEN.value(),
                    objectMapper.writeValueAsString(R.fail(errorCode)));
        } catch (Exception e) {
            log.error("写入无权限响应失败", e);
        }
    }
}
