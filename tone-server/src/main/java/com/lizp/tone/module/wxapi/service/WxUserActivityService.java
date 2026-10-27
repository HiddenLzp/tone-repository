package com.lizp.tone.module.wxapi.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lizp.tone.module.wxapi.entity.AppWxUser;
import com.lizp.tone.module.wxapi.mapper.AppWxUserMapper;
import com.lizp.tone.utils.AddressUtils;
import com.lizp.tone.utils.IpUtils;
import com.lizp.tone.utils.RedisUtils;
import com.lizp.tone.utils.ServletUtils;
import cn.hutool.core.util.StrUtil;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;

/**
 * 微信用户访问信息采集：IP / 归属地 / User-Agent / 访问次数 / 最近活跃时间。
 *
 * <p>背景：个人主体小程序拿不到微信官方的昵称头像接口，用户画像只能被动采集——
 * 每次请求里现成的信息就是 IP（归属地用模板自带的 ip2region 离线库解析）和
 * 请求头 User-Agent（微信内置浏览器 UA，含设备与微信版本）。
 *
 * <p>三个设计点：
 * <ol>
 *   <li>节流：同一个 openId 5 分钟内只记一次（Redis setIfAbsent 原子判断），
 *       否则每个 API 请求都写一次库，量起来后纯浪费；visit_count 因此的语义是
 *       "活跃时段数"而非原始请求数；</li>
 *   <li>异步：写库挪到模板的 taskExecutor（TTL 包装池），
 *       采集动作绝不能拖慢被拦截的业务请求；openId 以参数传递而非依赖 ThreadLocal，
 *       规避线程池上下文串号问题；</li>
 *   <li>静默失败：统计通道的任何异常只打 WARN，不影响业务请求。</li>
 * </ol>
 */
@Slf4j
@Service
public class WxUserActivityService {

    /** 同一用户两次记录的最小间隔；键 10 分钟过期，跨天自然失效 */
    private static final Duration THROTTLE = Duration.ofMinutes(5);

    private final RedisUtils redisUtils;
    private final AppWxUserMapper appWxUserMapper;

    /** 复用模板的 TTL 包装池（@Resource 按 bean 名注入，避免与 agentStreamExecutor 混淆） */
    @Resource(name = "taskExecutor")
    private ExecutorService taskExecutor;

    public WxUserActivityService(RedisUtils redisUtils, AppWxUserMapper appWxUserMapper) {
        this.redisUtils = redisUtils;
        this.appWxUserMapper = appWxUserMapper;
    }

    /**
     * 记录一次访问。在请求线程上调用（拦截器/登录逻辑里）：
     * 先在当前线程做 Redis 节流判断并顺手取 IP/UA（只能在这里取——异步线程里
     * HttpServletRequest 已随请求结束失效），写库则挪到后台线程。
     */
    public void recordAccessAsync(String openId) {
        if (openId == null || openId.isBlank()) {
            return;
        }
        // 节流：5 分钟内已记录过则直接返回
        if (!redisUtils.setIfAbsent("wxuser:active:" + openId, "1", THROTTLE.toMinutes(), TimeUnit.MINUTES)) {
            return;
        }
        HttpServletRequest request = ServletUtils.getRequest();
        String ip = request == null ? null : IpUtils.getIp(request);
        String ua = request == null ? null : StrUtil.maxLength(request.getHeader("User-Agent"), 250);
        taskExecutor.execute(() -> doRecord(openId, ip, ua));
    }

    /** 后台写库：更新最近 IP/归属地/UA/活跃时间，访问次数 +1 */
    private void doRecord(String openId, String ip, String ua) {
        try {
            AppWxUser user = appWxUserMapper.selectOne(
                    Wrappers.<AppWxUser>lambdaQuery().eq(AppWxUser::getOpenId, openId));
            if (user == null) {
                // 用户行在登录时创建；理论上不会走到这里，防御一下
                log.debug("访问记录跳过：用户不存在 openId={}", openId);
                return;
            }
            user.setLastIp(ip);
            // ip2region 离线解析；内网/解析失败时 AddressUtils 内部返回"内网IP/未知"之类的占位
            user.setLastLocation(ip == null ? null : AddressUtils.getAddress(ip));
            user.setLastUa(ua);
            user.setVisitCount((user.getVisitCount() == null ? 0 : user.getVisitCount()) + 1);
            user.setLastActiveAt(LocalDateTime.now());
            appWxUserMapper.updateById(user);
        } catch (Exception e) {
            log.warn("微信用户访问记录写入失败 openId={}", openId, e);
        }
    }
}
