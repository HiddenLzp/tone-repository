package com.lizp.tone.module.monitor;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lizp.tone.common.PageResult;
import com.lizp.tone.module.monitor.dto.LoginLogQuery;
import com.lizp.tone.module.monitor.entity.SysLoginLog;
import com.lizp.tone.module.monitor.mapper.SysLoginLogMapper;
import com.lizp.tone.utils.ClientInfo;
import cn.hutool.core.util.StrUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 登录日志服务。
 *
 * <p>记录动作必须是异步 + 独立事务：登录主流程不能因为写日志失败而失败，
 * 也不能因为登录失败回滚而把"失败原因"这条日志一起回滚掉（否则排查问题时反而没线索）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LoginLogService {

    private final SysLoginLogMapper loginLogMapper;

    /**
     * 记录一次登录尝试。
     *
     * <p>入参是 {@link ClientInfo} 快照而不是 {@code HttpServletRequest}：
     * 本方法跑在异步线程里，请求对象那时已被 Tomcat 回收，直接读会抛异常。
     * 调用方必须在请求线程内先 {@code ClientInfo.capture(request)}。
     *
     * @param username 登录账号
     * @param status   0成功 1失败
     * @param msg      失败原因
     * @param client   客户端信息快照
     */
    @Async("taskExecutor")
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void record(String username, Integer status, String msg, ClientInfo client) {
        try {
            ClientInfo info = client == null ? ClientInfo.empty() : client;
            SysLoginLog entity = new SysLoginLog();
            entity.setUsername(username);
            entity.setStatus(status);
            entity.setMsg(StrUtil.maxLength(msg, 250));
            entity.setLoginTime(LocalDateTime.now());
            entity.setIp(info.ip());
            entity.setLocation(info.location());
            entity.setBrowser(info.browser());
            entity.setOs(info.os());
            loginLogMapper.insert(entity);
        } catch (Exception e) {
            // 日志写入失败只记不抛：不能因为日志问题影响登录
            log.error("写入登录日志失败 username={}", username, e);
        }
    }

    public PageResult<SysLoginLog> page(LoginLogQuery query) {
        IPage<SysLoginLog> page = new Page<>(query.getPageNum(), query.getPageSize());
        LambdaQueryWrapper<SysLoginLog> wrapper = new LambdaQueryWrapper<SysLoginLog>()
                .like(StrUtil.isNotBlank(query.getUsername()), SysLoginLog::getUsername, query.getUsername())
                .like(StrUtil.isNotBlank(query.getIp()), SysLoginLog::getIp, query.getIp())
                .eq(query.getStatus() != null, SysLoginLog::getStatus, query.getStatus())
                .ge(query.getBeginTime() != null, SysLoginLog::getLoginTime, query.getBeginTime())
                .le(query.getEndTime() != null, SysLoginLog::getLoginTime, query.getEndTime())
                .orderByDesc(SysLoginLog::getLoginTime);
        return PageResult.of(loginLogMapper.selectPage(page, wrapper));
    }

    @Transactional(rollbackFor = Exception.class)
    public int deleteByIds(java.util.List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return 0;
        }
        return loginLogMapper.deleteBatchIds(ids);
    }
}
