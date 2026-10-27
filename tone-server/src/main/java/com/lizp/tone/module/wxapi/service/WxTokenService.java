package com.lizp.tone.module.wxapi.service;

import com.lizp.tone.common.BaseException;
import com.lizp.tone.common.ErrorCode;
import com.lizp.tone.module.wxapi.config.WxProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * 小程序端 JWT：与管理台的令牌体系完全独立（不同密钥、不同颁发方）。
 */
@Service
@RequiredArgsConstructor
public class WxTokenService {

    private final WxProperties wxProperties;

    private SecretKey key() {
        return Keys.hmacShaKeyFor(wxProperties.getTokenSecret().getBytes(StandardCharsets.UTF_8));
    }

    public String issue(String openId) {
        Date now = new Date();
        Date expire = new Date(now.getTime() + wxProperties.getTokenExpireDays() * 24L * 3600 * 1000);
        return Jwts.builder()
                .subject(openId)
                .issuedAt(now)
                .expiration(expire)
                .signWith(key())
                .compact();
    }

    /** 校验并取出 openId；非法/过期抛 WX_TOKEN_INVALID */
    public String parse(String token) {
        try {
            Claims claims = Jwts.parser().verifyWith(key()).build()
                    .parseSignedClaims(token).getPayload();
            return claims.getSubject();
        } catch (Exception e) {
            throw new BaseException(ErrorCode.WX_TOKEN_INVALID);
        }
    }
}
