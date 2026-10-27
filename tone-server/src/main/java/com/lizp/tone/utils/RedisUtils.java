package com.lizp.tone.utils;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.ReturnType;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Redis 工具类。
 *
 * <p>封装了脚手架里最常用的操作，业务模块直接注入即可，不用重复写 opsForValue() 那一长串。
 * 所有方法对异常做「吞掉 + 记日志」处理：Redis 抖动不应该让业务请求直接 500。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisUtils {

    private final RedisTemplate<String, Object> redisTemplate;
    /**
     * 脚本专用模板。
     *
     * <p>为什么不能直接用上面的 {@code redisTemplate} 跑 Lua：
     * {@code RedisTemplate#execute(script, keys, args)} 会用 **valueSerializer** 序列化 ARGV，
     * 而这里配的是 Jackson（JSON）——数字 60 会被序列化成带引号的 {@code "60"}，
     * 到了 Redis 里 {@code expire key "60"} 直接报
     * "value is not an integer or out of range"。
     *
     * <p>更阴的是：Lua 脚本里命令报错**不会回滚已执行的命令**，
     * 于是 incr 生效、expire 静默失败 → 计数器没有 TTL，只增不减。
     * 表现为「登录被限流，等 1 分钟、10 分钟再试都还是被限流」，只能手动删 key。
     * 所以脚本一律走 StringRedisTemplate（String 序列化），下面还更进一步自己转字节，
     * 连返回值反序列化都绕开。
     */
    private final StringRedisTemplate stringRedisTemplate;

    /** 释放锁的 Lua 脚本：先比对 value 再删除，避免误删别人持的锁 */
    private static final String UNLOCK_SCRIPT =
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end";
    private static final byte[] UNLOCK_SCRIPT_BYTES = UNLOCK_SCRIPT.getBytes(StandardCharsets.UTF_8);

    // ==================== 通用 ====================

    public boolean expire(String key, long timeout, TimeUnit unit) {
        try {
            return Boolean.TRUE.equals(redisTemplate.expire(key, timeout, unit));
        } catch (Exception e) {
            log.error("设置过期时间失败 key={}", key, e);
            return false;
        }
    }

    public long getExpire(String key) {
        try {
            Long expire = redisTemplate.getExpire(key, TimeUnit.SECONDS);
            return expire == null ? -2 : expire;
        } catch (Exception e) {
            log.error("获取过期时间失败 key={}", key, e);
            return -2;
        }
    }

    public boolean hasKey(String key) {
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(key));
        } catch (Exception e) {
            log.error("判断 key 是否存在失败 key={}", key, e);
            return false;
        }
    }

    public void delete(String... keys) {
        if (keys == null || keys.length == 0) {
            return;
        }
        try {
            redisTemplate.delete(List.of(keys));
        } catch (Exception e) {
            log.error("删除 key 失败 keys={}", List.of(keys), e);
        }
    }

    public void delete(Collection<String> keys) {
        if (keys == null || keys.isEmpty()) {
            return;
        }
        try {
            redisTemplate.delete(keys);
        } catch (Exception e) {
            log.error("删除 key 失败 keys={}", keys, e);
        }
    }

    public Set<String> keys(String pattern) {
        try {
            return redisTemplate.keys(pattern);
        } catch (Exception e) {
            log.error("查询 key 失败 pattern={}", pattern, e);
            return Set.of();
        }
    }

    // ==================== String ====================

    public void set(String key, Object value) {
        try {
            redisTemplate.opsForValue().set(key, value);
        } catch (Exception e) {
            log.error("写入缓存失败 key={}", key, e);
        }
    }

    public void set(String key, Object value, long timeout, TimeUnit unit) {
        try {
            redisTemplate.opsForValue().set(key, value, timeout, unit);
        } catch (Exception e) {
            log.error("写入缓存失败 key={} timeout={}", key, timeout, e);
        }
    }

    public void set(String key, Object value, Duration duration) {
        set(key, value, duration.toMillis(), TimeUnit.MILLISECONDS);
    }

    public Object get(String key) {
        try {
            return redisTemplate.opsForValue().get(key);
        } catch (Exception e) {
            log.error("读取缓存失败 key={}", key, e);
            return null;
        }
    }

    public String getStr(String key) {
        Object value = get(key);
        return value == null ? null : String.valueOf(value);
    }

    /**
     * 原子性写入：仅当 key 不存在时才写入，等价于 SET NX PX。
     * 加锁场景必须用这个方法，不能用 hasKey + set 两步（非原子，高并发下会重复加锁）。
     */
    public boolean setIfAbsent(String key, Object value, long timeout, TimeUnit unit) {
        try {
            return Boolean.TRUE.equals(redisTemplate.opsForValue()
                    .setIfAbsent(key, value, timeout, unit));
        } catch (Exception e) {
            log.error("原子写入失败 key={}", key, e);
            return false;
        }
    }

    /** 递增，返回新值 */
    public long incr(String key) {
        try {
            Long value = redisTemplate.opsForValue().increment(key);
            return value == null ? 0 : value;
        } catch (Exception e) {
            log.error("自增失败 key={}", key, e);
            return 0;
        }
    }

    public long incr(String key, long delta) {
        try {
            Long value = redisTemplate.opsForValue().increment(key, delta);
            return value == null ? 0 : value;
        } catch (Exception e) {
            log.error("自增失败 key={}", key, e);
            return 0;
        }
    }

    public long decr(String key) {
        return incr(key, -1L);
    }

    /**
     * 计数 + 设置过期时间，Lua 保证原子性（分开调用 incr/expire 时，进程中断会留下永不过期的计数器）。
     *
     * <p>过期判断用 {@code ttl < 0} 而不是 {@code c == 1}：
     * 只要这个 key 没有 TTL（首次创建、或历史上 expire 失败留下的脏 key），
     * 每次请求都会再补一次过期时间，计数器不会无限存活。
     * 用 {@code c == 1} 的话，「第一次设置失败」就永远没有第二次机会了——这正是本次事故的形状。
     *
     * <p>{@code tonumber} 是为了兜住脏参数：宁可这次不设过期（下次再补），
     * 也不要让脚本报错把整个调用打回 0（那等于限流失效）。
     */
    private static final String INCR_EXPIRE_SCRIPT =
            "local c = redis.call('incr', KEYS[1]) " +
                    "local ttl = redis.call('ttl', KEYS[1]) " +
                    "if ttl < 0 then " +
                    "  local t = tonumber(ARGV[1]) " +
                    "  if t ~= nil then redis.call('expire', KEYS[1], t) end " +
                    "end " +
                    "return c";
    private static final byte[] INCR_EXPIRE_SCRIPT_BYTES =
            INCR_EXPIRE_SCRIPT.getBytes(StandardCharsets.UTF_8);

    /** 走底层连接执行脚本：keys/args 自己转字节，不经过任何序列化器 */
    private Long evalLong(String key, byte[] scriptBytes, String arg) {
        return stringRedisTemplate.execute((RedisCallback<Long>) connection -> {
            Object result = connection.eval(scriptBytes, ReturnType.INTEGER, 1,
                    key.getBytes(StandardCharsets.UTF_8),
                    arg.getBytes(StandardCharsets.UTF_8));
            return result instanceof Number number ? number.longValue() : null;
        });
    }

    public long incrWithExpire(String key, long expireSeconds) {
        try {
            Long result = evalLong(key, INCR_EXPIRE_SCRIPT_BYTES, String.valueOf(expireSeconds));
            return result == null ? 0 : result;
        } catch (Exception e) {
            log.error("计数失败 key={}", key, e);
            // 失败放行：Redis 抖动不该把所有人挡在门外
            return 0;
        }
    }

    // ==================== Hash ====================

    public void hSet(String key, String hashKey, Object value) {
        try {
            redisTemplate.opsForHash().put(key, hashKey, value);
        } catch (Exception e) {
            log.error("写入 hash 失败 key={} hashKey={}", key, hashKey, e);
        }
    }

    public Object hGet(String key, String hashKey) {
        try {
            return redisTemplate.opsForHash().get(key, hashKey);
        } catch (Exception e) {
            log.error("读取 hash 失败 key={} hashKey={}", key, hashKey, e);
            return null;
        }
    }

    public void hDel(String key, Object... hashKeys) {
        try {
            redisTemplate.opsForHash().delete(key, hashKeys);
        } catch (Exception e) {
            log.error("删除 hash 失败 key={}", key, e);
        }
    }

    public Map<Object, Object> hGetAll(String key) {
        try {
            return redisTemplate.opsForHash().entries(key);
        } catch (Exception e) {
            log.error("读取 hash 全部失败 key={}", key, e);
            return Map.of();
        }
    }

    // ==================== Set / List ====================

    public void sAdd(String key, Object... values) {
        try {
            redisTemplate.opsForSet().add(key, values);
        } catch (Exception e) {
            log.error("写入 set 失败 key={}", key, e);
        }
    }

    public Set<Object> sMembers(String key) {
        try {
            return redisTemplate.opsForSet().members(key);
        } catch (Exception e) {
            log.error("读取 set 失败 key={}", key, e);
            return Set.of();
        }
    }

    public void lPush(String key, Object value) {
        try {
            redisTemplate.opsForList().leftPush(key, value);
        } catch (Exception e) {
            log.error("写入 list 失败 key={}", key, e);
        }
    }

    public List<Object> lRange(String key, long start, long end) {
        try {
            return redisTemplate.opsForList().range(key, start, end);
        } catch (Exception e) {
            log.error("读取 list 失败 key={}", key, e);
            return List.of();
        }
    }

    // ==================== 分布式锁 ====================

    /**
     * 尝试获取分布式锁。
     *
     * <p>注意：锁的 value 必须全局唯一（一般用 UUID），否则可能出现
     * 「A 的锁过期后被 B 持有，A 再释放时把 B 的锁删掉」的问题。
     */
    public boolean tryLock(String lockKey, String requestId, long timeout, TimeUnit unit) {
        // 锁值走 StringRedisTemplate 存裸字符串：解锁脚本比对的是裸字符串，
        // 两边必须用同一套序列化（用 JSON 存 + JSON 传也能对上，但那属于巧合，换个人就踩坑）
        try {
            return Boolean.TRUE.equals(stringRedisTemplate.opsForValue()
                    .setIfAbsent(lockKey, requestId, timeout, unit));
        } catch (Exception e) {
            log.error("加锁失败 key={}", lockKey, e);
            return false;
        }
    }

    /** 释放锁：Lua 保证「比对 + 删除」的原子性 */
    public boolean unlock(String lockKey, String requestId) {
        try {
            Long result = evalLong(lockKey, UNLOCK_SCRIPT_BYTES, requestId);
            return result != null && result > 0;
        } catch (Exception e) {
            log.error("释放锁失败 key={}", lockKey, e);
            return false;
        }
    }
}
