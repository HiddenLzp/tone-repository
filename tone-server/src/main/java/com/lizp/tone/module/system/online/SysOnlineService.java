package com.lizp.tone.module.system.online;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lizp.tone.common.BaseException;
import com.lizp.tone.common.Constants;
import com.lizp.tone.common.ErrorCode;
import com.lizp.tone.common.PageResult;
import com.lizp.tone.config.BaseProperties;
import com.lizp.tone.context.LogContext;
import com.lizp.tone.context.UserContextHolder;
import com.lizp.tone.module.system.user.entity.SysUserToken;
import com.lizp.tone.module.system.user.mapper.SysUserTokenMapper;
import com.lizp.tone.security.TokenVersionManager;
import com.lizp.tone.utils.RedisUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * 在线用户服务。
 *
 * <p>会话记录来自 sys_user_token（每次登录写一条），一个账号多端登录就有多条——
 * 这是按「会话」计的，不是脏数据。
 *
 * <p>下线有两种粒度，别混用：
 * <ul>
 *   <li><b>会话级</b>：{@link #kick(Long)} / {@link #kickOwn(Long, Long, String)}，
 *       删掉这一条 refreshToken + 写会话吊销名单，只影响选中的那一台设备；</li>
 *   <li><b>用户级</b>：{@link #kickByUserId(Long)}，令牌版本 +1，
 *       该用户所有设备的 accessToken 立刻全部失效（改密、停用、管理员一键下线用）。</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysOnlineService {

    private final SysUserTokenMapper userTokenMapper;
    private final RedisUtils redisUtils;
    private final TokenVersionManager tokenVersionManager;
    private final BaseProperties properties;

    public PageResult<SysUserToken> page(long pageNum, long pageSize, String username, String ip) {
        IPage<SysUserToken> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<SysUserToken> wrapper = new LambdaQueryWrapper<SysUserToken>()
                .like(StrUtil.isNotBlank(username), SysUserToken::getUsername, username)
                .like(StrUtil.isNotBlank(ip), SysUserToken::getIp, ip)
                .eq(SysUserToken::getStatus, 0)
                .gt(SysUserToken::getRefreshExpireTime, LocalDateTime.now())
                .orderByDesc(SysUserToken::getLoginTime);
        IPage<SysUserToken> result = userTokenMapper.selectPage(page, wrapper);
        // 标出「就是你自己在用的这一条」，避免管理员把自己踢了还不知道
        String currentJti = currentSessionId();
        result.getRecords().forEach(r -> r.setCurrent(isCurrent(r, currentJti)));
        return PageResult.of(result);
    }

    /** 在线数量，首页大盘用 */
    public long countOnline() {
        return userTokenMapper.selectCount(new LambdaQueryWrapper<SysUserToken>()
                .eq(SysUserToken::getStatus, 0)
                .gt(SysUserToken::getRefreshExpireTime, LocalDateTime.now()));
    }

    /**
     * 强制下线：只踢选中的这一条会话，该用户其它设备不受影响。
     *
     * @param tokenId sys_user_token 主键
     */
    @Transactional(rollbackFor = Exception.class)
    public void kick(Long tokenId) {
        SysUserToken record = userTokenMapper.selectById(tokenId);
        if (record == null) {
            throw new BaseException(ErrorCode.DATA_NOT_FOUND, "会话不存在");
        }
        if (Constants.SUPER_ADMIN_USER_ID.equals(record.getUserId())) {
            throw new BaseException(ErrorCode.SUPER_ADMIN_PROTECTED, "内置管理员不允许强制下线");
        }
        // 操作日志里要能看出"踢的是谁"：入参只有一个 tokenId，查不出账号
        LogContext.put("targetUser", record.getUsername());
        LogContext.put("targetUserId", record.getUserId());
        LogContext.put("targetIp", record.getIp());
        LogContext.put("targetSessionId", tokenId);
        LogContext.put("targetLoginTime", record.getLoginTime());
        LogContext.put("scope", "单条会话");
        revokeSession(record.getJti(), record.getRefreshExpireTime());
        markStatus(tokenId, 3);
    }

    /** 按用户ID下线全部会话（改密、停用、管理员一键下线）：令牌版本 +1，所有设备一起失效 */
    public void kickByUserId(Long userId) {
        List<SysUserToken> sessions = userTokenMapper.selectList(new LambdaQueryWrapper<SysUserToken>()
                .eq(SysUserToken::getUserId, userId)
                .eq(SysUserToken::getStatus, 0)
                .gt(SysUserToken::getRefreshExpireTime, LocalDateTime.now()));
        tokenVersionManager.invalidate(userId);
        redisUtils.delete(String.format(Constants.KEY_LOGIN_USER, userId));
        SysUserToken update = new SysUserToken();
        update.setStatus(3);
        userTokenMapper.update(update, new LambdaUpdateWrapper<SysUserToken>()
                .eq(SysUserToken::getUserId, userId)
                .eq(SysUserToken::getStatus, 0));
        LogContext.put("targetUserId", userId);
        LogContext.put("targetUser", sessions.stream().findFirst().map(SysUserToken::getUsername).orElse(null));
        LogContext.put("targetSessionCount", sessions.size());
        LogContext.put("scope", "全部会话");
    }

    /** 我的登录设备：当前用户自己的在线会话，当前这条排在最前面 */
    public List<SysUserToken> mySessions(Long userId, String currentJti) {
        List<SysUserToken> list = userTokenMapper.selectList(new LambdaQueryWrapper<SysUserToken>()
                .eq(SysUserToken::getUserId, userId)
                .eq(SysUserToken::getStatus, 0)
                .gt(SysUserToken::getRefreshExpireTime, LocalDateTime.now())
                .orderByDesc(SysUserToken::getLoginTime));
        list.forEach(r -> r.setCurrent(isCurrent(r, currentJti)));
        list.sort(Comparator.comparing((SysUserToken r) -> !Boolean.TRUE.equals(r.getCurrent()))
                .thenComparing(SysUserToken::getLoginTime, Comparator.reverseOrder()));
        return list;
    }

    /**
     * 下线自己的某台设备。
     * 不允许下线当前会话——那等于把自己踢下去，应该走「退出登录」。
     */
    public void kickOwn(Long userId, Long tokenId, String currentJti) {
        SysUserToken record = userTokenMapper.selectById(tokenId);
        if (record == null || !userId.equals(record.getUserId())) {
            throw new BaseException(ErrorCode.DATA_NOT_FOUND, "会话不存在");
        }
        if (isCurrent(record, currentJti)) {
            throw new BaseException(ErrorCode.PARAM_INVALID, "不能下线当前设备，请通过右上角退出登录");
        }
        LogContext.put("targetUser", record.getUsername());
        LogContext.put("targetIp", record.getIp());
        LogContext.put("targetSessionId", tokenId);
        LogContext.put("scope", "本人的其它设备");
        revokeSession(record.getJti(), record.getRefreshExpireTime());
        markStatus(tokenId, 3);
    }

    /**
     * 吊销单条会话：删 refreshToken（含轮换宽限期那份）+ 写会话吊销名单。
     *
     * <p>accessToken 是无状态的，不记名单的话它在过期前仍然可用，
     * 表现就是「已经点了强制下线，对方还能接着操作」。
     */
    private void revokeSession(String jti, LocalDateTime refreshExpireTime) {
        if (StrUtil.isBlank(jti)) {
            return;
        }
        redisUtils.delete(String.format(Constants.KEY_REFRESH, jti));
        redisUtils.delete(String.format(Constants.KEY_REFRESH_GRACE, jti));
        redisUtils.set(String.format(Constants.KEY_SESSION_REVOKED, jti), 1,
                remainDays(refreshExpireTime), TimeUnit.DAYS);
    }

    /** 吊销名单的存活时间：跟着会话本身的剩余寿命走，不用长期占着 Redis */
    private long remainDays(LocalDateTime refreshExpireTime) {
        long max = properties.getSecurity().getRefreshExpire();
        if (refreshExpireTime == null) {
            return Math.max(max, 1);
        }
        long days = Duration.between(LocalDateTime.now(), refreshExpireTime).toDays();
        return Math.min(Math.max(days, 1), Math.max(max, 1));
    }

    private void markStatus(Long tokenId, int status) {
        SysUserToken update = new SysUserToken();
        update.setId(tokenId);
        update.setStatus(status);
        userTokenMapper.updateById(update);
    }

    private boolean isCurrent(SysUserToken record, String currentJti) {
        return currentJti != null && Objects.equals(record.getJti(), currentJti);
    }

    /** 当前请求的会话ID；过滤器从 accessToken 的 sid 声明回填的 */
    private String currentSessionId() {
        var user = UserContextHolder.get();
        return user == null ? null : user.getJti();
    }

    /**
     * 清理过期会话记录：已登出/已过期/被强退且超过保留期的记录。
     * 由定时任务调用，避免这张表无限膨胀。
     */
    public int cleanExpired(int retainDays) {
        LocalDateTime deadline = LocalDateTime.now().minusDays(retainDays);
        List<SysUserToken> expired = userTokenMapper.selectList(new LambdaQueryWrapper<SysUserToken>()
                .ne(SysUserToken::getStatus, 0)
                .lt(SysUserToken::getLoginTime, deadline));
        if (expired.isEmpty()) {
            return 0;
        }
        List<Long> ids = expired.stream().map(SysUserToken::getId).toList();
        userTokenMapper.deleteBatchIds(ids);
        return ids.size();
    }
}
