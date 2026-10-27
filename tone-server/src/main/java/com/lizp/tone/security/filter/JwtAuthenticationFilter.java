package com.lizp.tone.security.filter;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lizp.tone.common.Constants;
import com.lizp.tone.common.ErrorCode;
import com.lizp.tone.common.R;
import com.lizp.tone.config.BaseProperties;
import com.lizp.tone.context.LogContext;
import com.lizp.tone.context.UserContextHolder;
import com.lizp.tone.security.JwtTokenProvider;
import com.lizp.tone.security.LoginUser;
import com.lizp.tone.security.LoginUserLoader;
import com.lizp.tone.utils.RedisUtils;
import com.lizp.tone.utils.ServletUtils;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * JWT 认证过滤器：把 accessToken 还原成 {@link LoginUser} 并放进 SecurityContext。
 *
 * <p>几个关键判定：
 * <ul>
 *   <li>白名单路径直接放行（登录、验证码、接口文档）；</li>
 *   <li>token 过期 → 401 + code 20002，前端收到后触发无感刷新；</li>
 *   <li>token 版本与 Redis 中不一致 → 401 + code 20005（已登出 / 被强退 / 改过密码）；</li>
 *   <li>首次登录需改密 → 403 + code 20010，前端跳强制改密页。</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER = "Bearer ";
    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    /** 强制改密期间允许访问的路径：只留改密、登出、看自己信息 */
    private static final List<String> MUST_CHANGE_PASSWORD_ALLOWED =
            List.of("/auth/logout", "/auth/change-password", "/auth/info");

    private final JwtTokenProvider tokenProvider;
    private final LoginUserLoader loginUserLoader;
    private final RedisUtils redisUtils;
    private final BaseProperties properties;
    private final ObjectMapper objectMapper;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // 预检请求不带自定义头，直接放行；否则跨域场景下前端每次请求都会先被 401 拦一次
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        return isWhiteListed(request.getServletPath());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String token = resolveToken(request);
        if (StrUtil.isBlank(token)) {
            // 没带 token：交给后面的授权环节判定，最终由 EntryPoint 返回 401
            chain.doFilter(request, response);
            return;
        }

        Claims claims;
        try {
            claims = tokenProvider.parse(token);
        } catch (JwtTokenProvider.TokenExpiredException e) {
            writeError(response, HttpStatus.UNAUTHORIZED, ErrorCode.TOKEN_EXPIRED);
            return;
        } catch (JwtTokenProvider.TokenInvalidException e) {
            writeError(response, HttpStatus.UNAUTHORIZED, ErrorCode.TOKEN_INVALID);
            return;
        }

        // 防止拿 refreshToken 当 accessToken 用
        if (!JwtTokenProvider.TYPE_ACCESS.equals(JwtTokenProvider.getType(claims))) {
            writeError(response, HttpStatus.UNAUTHORIZED, ErrorCode.TOKEN_INVALID);
            return;
        }

        Long userId = JwtTokenProvider.getUserId(claims);
        LoginUser loginUser = loadLoginUser(userId);
        if (loginUser == null) {
            writeError(response, HttpStatus.UNAUTHORIZED, ErrorCode.UNAUTHORIZED);
            return;
        }
        /*
         * 停用必须排在令牌版本之前判断。
         * 「停用」本身也会把令牌版本 +1（见 SysUserService.changeStatus），
         * 先判版本的话被停用的用户拿到的是「强制下线」，而真正的原因是账号停用了——
         * 提示说不清，用户只能去问管理员。
         */
        if (!loginUser.isEnabled()) {
            writeError(response, HttpStatus.UNAUTHORIZED, ErrorCode.ACCOUNT_DISABLED);
            return;
        }
        // 令牌版本比对：登出 / 改密 / 强退 / 禁用都会让 Redis 里的版本 +1，
        // 这样即使 accessToken 还没过期也会立刻失效，无需维护黑名单表。
        // 必须用 Objects.equals：两边都是 Integer，用 != 比的是引用，
        // 版本号只要超过 127 就会走出 Integer 缓存从而永远判定为不一致。
        if (!java.util.Objects.equals(JwtTokenProvider.getTokenVersion(claims), loginUser.getTokenVersion())) {
            writeError(response, HttpStatus.UNAUTHORIZED, ErrorCode.KICKED_OUT);
            return;
        }
        // 会话级吊销：只踢选中的那一台设备（登出、在线用户里单条强制下线走这里）。
        // 令牌版本是用户级的，管不了这个粒度，所以再补一层会话名单。
        String sessionId = JwtTokenProvider.getSessionId(claims);
        if (StrUtil.isNotBlank(sessionId)
                && redisUtils.hasKey(String.format(Constants.KEY_SESSION_REVOKED, sessionId))) {
            writeError(response, HttpStatus.UNAUTHORIZED, ErrorCode.KICKED_OUT);
            return;
        }
        /*
         * 把会话ID回填进 LoginUser：登出 / 刷新都靠它定位「是哪一条会话」。
         * 不能反过来把它写进 KEY_LOGIN_USER 缓存——那个 key 是按用户的，
         * 多端登录时 A 设备的会话ID 会被 B 设备覆盖，登出就会吊销错会话。
         */
        loginUser.setJti(sessionId);

        // 首次登录强制改密：除改密相关接口外一律拦下
        if (Boolean.TRUE.equals(loginUser.getMustChangePassword())
                && !MUST_CHANGE_PASSWORD_ALLOWED.contains(request.getServletPath())) {
            writeError(response, HttpStatus.FORBIDDEN, ErrorCode.PASSWORD_MUST_CHANGE);
            return;
        }

        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                loginUser, null, loginUser.getAuthorities());
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authentication);
        UserContextHolder.set(loginUser);

        try {
            chain.doFilter(request, response);
        } finally {
            // 必须清理：请求线程来自线程池，不清理会让下一个请求复用上一个用户的上下文。
            // LogContext 同理——它是给 LogAspect 补"操作了谁"的，
            // 只在被 @Log 切面取走时才算消费掉，没取走就必须在这里收尾。
            UserContextHolder.clear();
            LogContext.clear();
        }
    }

    /** 优先读 Redis 缓存；缓存失效（重启 / 过期）时回源数据库并重建缓存 */
    private LoginUser loadLoginUser(Long userId) {
        String key = String.format(Constants.KEY_LOGIN_USER, userId);
        Object cached = redisUtils.get(key);
        if (cached instanceof LoginUser loginUser) {
            return loginUser;
        }
        LoginUser loginUser = loginUserLoader.loadByUserId(userId);
        if (loginUser != null) {
            redisUtils.set(key, loginUser, properties.getSecurity().getRefreshExpire(), java.util.concurrent.TimeUnit.DAYS);
        }
        return loginUser;
    }

    private String resolveToken(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (StrUtil.isNotBlank(header) && header.startsWith(BEARER)) {
            return header.substring(BEARER.length());
        }
        return null;
    }

    private boolean isWhiteListed(String path) {
        List<String> whiteList = properties.getSecurity().getWhiteList();
        if (whiteList == null) {
            return false;
        }
        for (String pattern : whiteList) {
            if (PATH_MATCHER.match(pattern, path)) {
                return true;
            }
        }
        return false;
    }

    private void writeError(HttpServletResponse response, HttpStatus status, ErrorCode errorCode) throws IOException {
        R<Void> body = R.fail(errorCode);
        ServletUtils.renderJson(response, status.value(), objectMapper.writeValueAsString(body));
    }
}
