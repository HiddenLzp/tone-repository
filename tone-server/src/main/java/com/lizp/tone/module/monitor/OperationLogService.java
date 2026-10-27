package com.lizp.tone.module.monitor;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lizp.tone.common.PageResult;
import com.lizp.tone.module.monitor.dto.OperationLogQuery;
import com.lizp.tone.module.monitor.entity.SysOperationLog;
import com.lizp.tone.module.monitor.mapper.SysOperationLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** 操作日志服务 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OperationLogService {

    private final SysOperationLogMapper operationLogMapper;

    /**
     * 异步落库 + 独立事务。
     *
     * <p>为什么必须 REQUIRES_NEW：如果和业务方法同事务，业务抛异常回滚时
     * 会把这次操作的日志一起回滚掉，结果就是"出错的操作反而没留下痕迹"，
     * 完全违背了记录日志的初衷。
     */
    @Async("taskExecutor")
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void save(SysOperationLog entity) {
        try {
            operationLogMapper.insert(entity);
        } catch (Exception e) {
            log.error("写入操作日志失败", e);
        }
    }

    public PageResult<SysOperationLog> page(OperationLogQuery query) {
        IPage<SysOperationLog> page = new Page<>(query.getPageNum(), query.getPageSize());
        LambdaQueryWrapper<SysOperationLog> wrapper = new LambdaQueryWrapper<SysOperationLog>()
                .like(StrUtil.isNotBlank(query.getTitle()), SysOperationLog::getTitle, query.getTitle())
                .like(StrUtil.isNotBlank(query.getOperName()), SysOperationLog::getOperName, query.getOperName())
                .eq(query.getBusinessType() != null, SysOperationLog::getBusinessType, query.getBusinessType())
                .eq(query.getStatus() != null, SysOperationLog::getStatus, query.getStatus())
                .ge(query.getBeginTime() != null, SysOperationLog::getCreateTime, query.getBeginTime())
                .le(query.getEndTime() != null, SysOperationLog::getCreateTime, query.getEndTime())
                .orderByDesc(SysOperationLog::getCreateTime);
        return PageResult.of(operationLogMapper.selectPage(page, wrapper));
    }

    @Transactional(rollbackFor = Exception.class)
    public int deleteByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return 0;
        }
        return operationLogMapper.deleteBatchIds(ids);
    }
}
