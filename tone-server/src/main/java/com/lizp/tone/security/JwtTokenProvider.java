package com.lizp.tone.security;

import com.lizp.tone.config.BaseProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

/**
 * JWT 签发与解析。
 *
 * <p>载荷设计：
 * <ul>
 *   <li>{@code sub} 用户ID</li>
 *   <li>{@code jti} 令牌唯一ID（refresh 存 Redis 的 key 后缀；access 用于黑名单兜底）</li>
 *   <li>{@code tv}  令牌版本号，与 Redis 中的版本比对，实现「登出 / 改密 / 强退」后立即失效</li>
 *   <li>{@code sid} 会话ID（= 该会话 refreshToken 的 jti），用于按会话精确吊销</li>
 *   <li>{@code type} access / refresh，防止拿 refreshToken 当 accessToken 用</li>
 * </ul>
 */
@Slf4j
@Component
public class JwtTokenProvider {

    public static final String CLAIM_TYPE = "type";
    public static final String CLAIM_TOKEN_VERSION = "tv";
    public static final String CLAIM_USERNAME = "username";
    public static final String CLAIM_DEPT_ID = "deptId";
    /**
     * 会话ID：等于这一条会话的 refreshToken jti。
     * 有了它才能「只踢某台设备」——令牌版本是用户级的，一改全端失效，
     * 做不到按会话精确吊销。
     */
    public static final String CLAIM_SESSION_ID = "sid";
    public static final String TYPE_ACCESS = "access";
    public static final String TYPE_REFRESH = "refresh";

    private final SecretKey secretKey;
    private final BaseProperties properties;

    public JwtTokenProvider(BaseProperties properties) {
        this.properties = properties;
        // HS256 要求密钥至少 256 bit（32 字节），太短会在启动时就抛 WeakKeyException，
        // 属于「早点失败」而不是等到线上才发现
        this.secretKey = Keys.hmacShaKeyFor(properties.getSecurity().getJwtSecret().getBytes(StandardCharsets.UTF_8));
    }

    /** 生成 accessToken：短期有效，不落 Redis，靠签名自证 */
    public String generateAccessToken(LoginUser user, String jti) {
        Date now = new Date();
        Date expire = new Date(now.getTime() + properties.getSecurity().getAccessExpire() * 60 * 1000);
        JwtBuilder builder = Jwts.builder()
                .id(jti)
                .subject(String.valueOf(user.getUserId()))
                .claim(CLAIM_USERNAME, user.getUsername())
                .claim(CLAIM_DEPT_ID, user.getDeptId())
                .claim(CLAIM_TOKEN_VERSION, user.getTokenVersion())
                .claim(CLAIM_TYPE, TYPE_ACCESS);
        // 会话ID：登出 / 强制下线按它精确吊销。为空时不写，否则会序列化出 "null"
        if (user.getJti() != null && !user.getJti().isBlank()) {
            builder.claim(CLAIM_SESSION_ID, user.getJti());
        }
        return builder.issuedAt(now)
                .expiration(expire)
                .signWith(secretKey)
                .compact();
    }

    /** 生成 refreshToken：长期有效，落 Redis + 入库，可撤销 */
    public String generateRefreshToken(Long userId, String jti, Integer tokenVersion) {
        Date now = new Date();
        Date expire = new Date(now.getTime() + properties.getSecurity().getRefreshExpire() * 24 * 60 * 60 * 1000);
        return Jwts.builder()
                .id(jti)
                .subject(String.valueOf(userId))
                .claim(CLAIM_TOKEN_VERSION, tokenVersion)
                .claim(CLAIM_TYPE, TYPE_REFRESH)
                .issuedAt(now)
                .expiration(expire)
                .signWith(secretKey)
                .compact();
    }

    public String newJti() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 解析 token。
     *
     * @throws TokenExpiredException 已过期
     * @throws TokenInvalidException 签名错误 / 被篡改 / 类型不对
     */
    public Claims parse(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (ExpiredJwtException e) {
            throw new TokenExpiredException(e.getClaims());
        } catch (JwtException | IllegalArgumentException e) {
            throw new TokenInvalidException(e.getMessage(), e);
        }
    }

    /** 只在 token 过期时使用：ExpiredJwtException 里仍然能拿到 Claims（含 userId），刷新流程需要它 */
    public static class TokenExpiredException extends RuntimeException {
        private final transient Claims claims;

        public TokenExpiredException(Claims claims) {
            super("token 已过期");
            this.claims = claims;
        }

        public Claims getClaims() {
            return claims;
        }
    }

    public static class TokenInvalidException extends RuntimeException {
        public TokenInvalidException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public static Long getUserId(Claims claims) {
        return Long.valueOf(claims.getSubject());
    }

    public static Integer getTokenVersion(Claims claims) {
        Object v = claims.get(CLAIM_TOKEN_VERSION);
        return v == null ? 0 : Integer.parseInt(String.valueOf(v));
    }

    public static String getType(Claims claims) {
        return String.valueOf(claims.get(CLAIM_TYPE));
    }

    /** 会话ID；老令牌（没有 sid 声明）返回 null，走「令牌版本」那条兜底路径 */
    public static String getSessionId(Claims claims) {
        Object v = claims.get(CLAIM_SESSION_ID);
        return v == null ? null : String.valueOf(v);
    }
}
