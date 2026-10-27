package com.lizp.tone.module.job.task;

import com.lizp.tone.config.BaseProperties;
import com.lizp.tone.module.job.SysJobService;
import com.lizp.tone.module.monitor.mapper.SysLoginLogMapper;
import com.lizp.tone.module.monitor.mapper.SysOperationLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 日志清理任务：删除保留期之前的操作日志与登录日志。
 *
 * <p>为什么分批删而不是一条 DELETE 搞定：
 * 一次删几百万行会产生超大事务——undo 日志暴涨、主从延迟、锁等待，
 * 严重时整张表被锁住影响线上写入。分批删把压力摊开，代价只是多花一点时间。
 */
@Slf4j
@Component("logCleanTask")
@RequiredArgsConstructor
public class LogCleanTask {

    private final SysOperationLogMapper operationLogMapper;
    private final SysLoginLogMapper loginLogMapper;
    private final SysJobService jobService;
    private final BaseProperties properties;

    /** 对应 sys_job 里的 invoke_target：logCleanTask.cleanOperationLog() */
    public void cleanOperationLog() {
        jobService.runWithLock("cleanLog", this::doClean, 30 * 60);
    }

    private void doClean() {
        int months = properties.getLog().getRetentionMonths();
        int batchSize = properties.getLog().getCleanBatchSize();
        LocalDateTime deadline = LocalDateTime.now().minusMonths(months);
        log.info("开始清理 {} 个月前的日志，截止时间 {}", months, deadline);

        int total = cleanTable("sys_operation_log", () -> {
            long maxId = operationLogMapper.maxIdBefore(deadline);
            if (maxId <= 0) {
                return 0;
            }
            return operationLogMapper.deleteBatch(deadline, maxId, batchSize);
        });
        int loginTotal = cleanTable("sys_login_log", () -> {
            long maxId = loginLogMapper.maxIdBefore(deadline);
            if (maxId <= 0) {
                return 0;
            }
            return loginLogMapper.deleteBatch(deadline, maxId, batchSize);
        });
        log.info("日志清理完成，操作日志 {} 条，登录日志 {} 条", total, loginTotal);
    }

    /** 循环删除直到这一轮删不动为止；每批之间歇一下，给主库和从库留喘息时间 */
    private int cleanTable(String tableName, BatchDeleter deleter) {
        int total = 0;
        int rounds = 0;
        while (rounds++ < 1000) {
            int deleted;
            try {
                deleted = deleter.delete();
            } catch (Exception e) {
                log.error("清理表 {} 失败，已删除 {} 条", tableName, total, e);
                break;
            }
            total += deleted;
            if (deleted < 1) {
                break;
            }
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        return total;
    }

    private interface BatchDeleter {
        int delete();
    }
}
