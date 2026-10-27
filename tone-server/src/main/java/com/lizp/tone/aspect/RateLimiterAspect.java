package com.lizp.tone.aspect;

import com.lizp.tone.common.BaseException;
import com.lizp.tone.common.Constants;
import com.lizp.tone.common.ErrorCode;
import com.lizp.tone.context.UserContextHolder;
import com.lizp.tone.utils.IpUtils;
import com.lizp.tone.utils.RedisUtils;
import com.lizp.tone.utils.ServletUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

/**
 * 限流切面。
 *
 * <p>用 Redis 计数而不是本地 Guava RateLimiter：多实例部署时本地限流会失效
 * （每个实例各限各的，总量仍是 N 倍），必须有一个共享的计数中心。
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class RateLimiterAspect {

    private final RedisUtils redisUtils;

    @Around("@annotation(com.lizp.tone.aspect.RateLimiter)")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        RateLimiter annotation = signature.getMethod().getAnnotation(RateLimiter.class);
        if (annotation == null) {
            return joinPoint.proceed();
        }
        String key = buildKey(signature, annotation);
        long current = redisUtils.incrWithExpire(key, annotation.time());
        if (current > annotation.count()) {
            long ttl = enterCooldown(key, current, annotation);
            log.warn("触发限流 key={} count={}/{} 冷却剩余={}s", key, current, annotation.count(), ttl);
            // 秒数由这里统一拼，注解上的 message 里就不要再写"请 1 分钟后再试"了，否则会出现
            // "…请 1 分钟后再试（请 37 秒后再试）"这种自相矛盾的提示
            String message = ttl > 0
                    ? String.format("%s，请 %d 秒后再试", annotation.message(), ttl)
                    : annotation.message();
            throw new BaseException(ErrorCode.RATE_LIMIT, message);
        }
        return joinPoint.proceed();
    }

    /**
     * 触发限流后进入冷却：把过期时间重置成**完整的一个窗口**（从现在算起）。
     *
     * <p>为什么重置：计数器的 TTL 原本是从窗口内「第一次请求」算起的，
     * 用户被拦的时候窗口往往已经过去大半，剩下的等待时间是个随机数（可能 3 秒，也可能 59 秒）。
     * 提示写"请 1 分钟后再试"而实际只需等 3 秒，或者反过来让用户白等——两头都不对。
     * 重置之后，等待时间变成一个确定值：从被拦这一刻起，满一个窗口必定放行。
     *
     * <p>为什么**只重置一次**（{@code current == count + 1} 那一下）而不是每次被拦都续期：
     * 每次续期等于"点一下加 60 秒"，用户着急时连点几下就被锁到 5 分钟、10 分钟，
     * 而且这个时间是他自己点出来的，看起来跟限流出了 bug 没区别。
     * 冷却期内继续请求只计数、不续期，到点一定放行。
     *
     * @return 冷却剩余秒数；&lt;=0 表示这个 key 没有 TTL（历史脏 key，不该发生）
     */
    private long enterCooldown(String key, long current, RateLimiter annotation) {
        long ttl = redisUtils.getExpire(key);
        boolean firstHit = current == (long) annotation.count() + 1;
        if (firstHit || ttl < 0) {
            // 顺带兜住历史脏 key：老版本 expire 失败留下的无 TTL 计数器，
            // 到这里也会被补上过期时间，不会无限存活
            redisUtils.expire(key, annotation.time(), java.util.concurrent.TimeUnit.SECONDS);
            ttl = annotation.time();
        }
        return ttl;
    }

    private String buildKey(MethodSignature signature, RateLimiter annotation) {
        String prefix = annotation.key().isEmpty()
                ? signature.getDeclaringTypeName() + "." + signature.getMethod().getName()
                : annotation.key();
        String dimension = switch (annotation.limitType()) {
            case IP -> ip();
            case USER -> String.valueOf(UserContextHolder.getUserId() == null ? ip() : UserContextHolder.getUserId());
            case GLOBAL -> "global";
        };
        return String.format(Constants.KEY_RATE_LIMIT, prefix + ":" + dimension);
    }

    private String ip() {
        HttpServletRequest request = ServletUtils.getRequest();
        return request == null ? "unknown" : IpUtils.getIp(request);
    }
}
