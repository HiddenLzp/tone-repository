package com.lizp.tone.security;

import cn.hutool.core.util.StrUtil;
import com.lizp.tone.common.Constants;
import com.lizp.tone.utils.RedisUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 令牌版本号（token version，简称 tv）管理器。
 *
 * <p>作用：accessToken 是无状态的，签发后服务端没法单方面作废。
 * 给每个用户在 Redis 里维护一个自增版本号，写进 token 载荷；
 * 请求进来时比对「token 里的 tv」与「Redis 里的 tv」，不一致即判定失效。
 * 这样登出 / 改密 / 强退 / 禁用能立刻生效，且不需要维护 token 黑名单表。
 *
 * <p>为什么抽成独立组件：签发（AuthService）、加载（LoginUserLoader）、
 * 吊销（AuthService / SysUserService / SysOnlineService）三处都要用它，
 * 散在各处各写一份迟早会不一致。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TokenVersionManager {

    private final RedisUtils redisUtils;

    /** 取当前版本号；不存在则初始化为 1（不要在登录时自增，否则多端登录会互踢） */
    public Integer current(Long userId) {
        String key = String.format(Constants.KEY_TOKEN_VERSION, userId);
        Object value = redisUtils.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value != null && StrUtil.isNotBlank(value.toString())) {
            try {
                return Integer.parseInt(value.toString().trim());
            } catch (NumberFormatException e) {
                log.warn("令牌版本号异常，已重置 key={} value={}", key, value);
            }
        }
        redisUtils.set(key, 1);
        return 1;
    }

    /** 版本号 +1，使该用户手上所有 accessToken 立即失效 */
    public long invalidate(Long userId) {
        String key = String.format(Constants.KEY_TOKEN_VERSION, userId);
        if (!redisUtils.hasKey(key)) {
            redisUtils.set(key, 1);
            return 1;
        }
        return redisUtils.incr(key);
    }
}
