package com.lizp.tone.module.wxapi.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lizp.tone.common.ErrorCode;
import com.lizp.tone.common.R;
import com.lizp.tone.module.wxapi.service.WxTokenService;
import com.lizp.tone.module.wxapi.service.WxUserActivityService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * /api/** 的鉴权拦截器：校验小程序 JWT（Authorization: Bearer xxx），
 * 通过后把 openId 放进 WxUserContext。管理台的 Spring Security 体系不感知这些路径
 * （已加入 tone.security.white-list）。
 *
 * <p>顺带做访问信息采集（IP/归属地/UA/次数，节流异步）：拦截器是唯一能同时拿到
 * "已认证 openId"和"原始请求"的位置，这里不采就没地方采了。
 */
@Component
@RequiredArgsConstructor
public class WxAuthInterceptor implements HandlerInterceptor {

    private final WxTokenService wxTokenService;
    private final ObjectMapper objectMapper;
    private final WxUserActivityService wxUserActivityService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        String auth = request.getHeader("Authorization");
        if (auth == null || !auth.startsWith("Bearer ")) {
            return reject(response);
        }
        try {
            String openId = wxTokenService.parse(auth.substring(7).trim());
            WxUserContext.setOpenId(openId);
            // 节流 + 异步：内部 5 分钟去重，不影响请求耗时
            wxUserActivityService.recordAccessAsync(openId);
            return true;
        } catch (Exception e) {
            return reject(response);
        }
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        WxUserContext.clear();
    }

    private boolean reject(HttpServletResponse response) throws Exception {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(R.fail(ErrorCode.WX_TOKEN_INVALID)));
        return false;
    }
}
