package com.lizp.tone.module.job;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lizp.tone.common.BaseException;
import com.lizp.tone.common.Constants;
import com.lizp.tone.common.ErrorCode;
import com.lizp.tone.common.PageResult;
import com.lizp.tone.module.job.entity.SysJob;
import com.lizp.tone.module.job.entity.SysJobLog;
import com.lizp.tone.module.job.mapper.SysJobLogMapper;
import com.lizp.tone.module.job.mapper.SysJobMapper;
import com.lizp.tone.utils.RedisUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** 定时任务服务 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysJobService {

    /** 正在执行中的任务ID，用于实现「禁止并发」 */
    private static final Set<Long> RUNNING_JOBS = ConcurrentHashMap.newKeySet();

    private final SysJobMapper jobMapper;
    private final SysJobLogMapper jobLogMapper;
    private final CronTaskRegistrar cronTaskRegistrar;
    private final JobInvokeUtil jobInvokeUtil;
    private final RedisUtils redisUtils;

    /**
     * 应用启动后加载所有「正常」状态的任务。
     *
     * <p>必须等 ApplicationReadyEvent 再注册：注册器是在 ContextRefreshedEvent 阶段
     * 才由 Spring 注入的，放在 @PostConstruct 里会拿到 null。
     */
    @EventListener(ApplicationReadyEvent.class)
    public void init() {
        List<SysJob> jobs = jobMapper.selectList(new LambdaQueryWrapper<SysJob>()
                .eq(SysJob::getStatus, Constants.STATUS_NORMAL));
        int count = 0;
        for (SysJob job : jobs) {
            try {
                register(job);
                count++;
            } catch (Exception e) {
                log.error("加载定时任务失败 jobId={} name={}", job.getId(), job.getJobName(), e);
            }
        }
        log.info("定时任务加载完成，共 {} 个", count);
    }

    public PageResult<SysJob> page(long pageNum, long pageSize, String jobName, String jobGroup, Integer status) {
        IPage<SysJob> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<SysJob> wrapper = new LambdaQueryWrapper<SysJob>()
                .like(StrUtil.isNotBlank(jobName), SysJob::getJobName, jobName)
                .eq(StrUtil.isNotBlank(jobGroup), SysJob::getJobGroup, jobGroup)
                .eq(status != null, SysJob::getStatus, status)
                .orderByAsc(SysJob::getId);
        return PageResult.of(jobMapper.selectPage(page, wrapper));
    }

    public SysJob getById(Long id) {
        return jobMapper.selectById(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public Long add(SysJob job) {
        validateCron(job.getCron());
        jobMapper.insert(job);
        if (Constants.STATUS_NORMAL.equals(job.getStatus())) {
            register(job);
        }
        return job.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(SysJob job) {
        validateCron(job.getCron());
        jobMapper.updateById(job);
        cronTaskRegistrar.removeCronTask(job.getId());
        if (Constants.STATUS_NORMAL.equals(job.getStatus())) {
            register(job);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        ids.forEach(cronTaskRegistrar::removeCronTask);
        jobMapper.deleteBatchIds(ids);
    }

    @Transactional(rollbackFor = Exception.class)
    public void changeStatus(Long id, Integer status) {
        SysJob job = jobMapper.selectById(id);
        if (job == null) {
            throw new BaseException(ErrorCode.DATA_NOT_FOUND);
        }
        job.setStatus(status);
        jobMapper.updateById(job);
        if (Constants.STATUS_NORMAL.equals(status)) {
            register(job);
        } else {
            cronTaskRegistrar.removeCronTask(id);
        }
    }

    /** 立即执行一次（不影响原调度） */
    public void runOnce(Long id) {
        SysJob job = jobMapper.selectById(id);
        if (job == null) {
            throw new BaseException(ErrorCode.DATA_NOT_FOUND);
        }
        execute(job);
    }

    private void register(SysJob job) {
        cronTaskRegistrar.addCronTask(job.getId(), () -> execute(job), job.getCron());
    }

    /**
     * 任务执行模板：并发控制 → 执行 → 写任务日志。
     * 单实例下用内存 Set 判重；多实例部署时任务本身还应配合 Redis 分布式锁
     * （如日志清理任务的 {@code JobLockExecutor}）。
     */
    private void execute(SysJob job) {
        if (Integer.valueOf(1).equals(job.getConcurrent()) && !RUNNING_JOBS.add(job.getId())) {
            log.warn("任务 {} 上一次执行尚未结束，本次跳过", job.getJobName());
            return;
        }
        SysJobLog jobLog = new SysJobLog();
        jobLog.setJobId(job.getId());
        jobLog.setJobName(job.getJobName());
        jobLog.setJobGroup(job.getJobGroup());
        jobLog.setInvokeTarget(job.getInvokeTarget());
        jobLog.setStartTime(LocalDateTime.now());
        long start = System.currentTimeMillis();
        try {
            jobInvokeUtil.invoke(job.getInvokeTarget());
            jobLog.setStatus(0);
            jobLog.setJobMessage(job.getJobName() + " 执行成功");
        } catch (Exception e) {
            jobLog.setStatus(1);
            jobLog.setJobMessage("执行失败");
            jobLog.setExceptionInfo(StrUtil.maxLength(String.valueOf(e.getMessage()), 1900));
            log.error("定时任务执行失败 jobId={} name={}", job.getId(), job.getJobName(), e);
        } finally {
            RUNNING_JOBS.remove(job.getId());
            jobLog.setEndTime(LocalDateTime.now());
            jobLog.setCostTime(System.currentTimeMillis() - start);
            try {
                jobLogMapper.insert(jobLog);
            } catch (Exception e) {
                log.error("写入任务日志失败", e);
            }
        }
    }

    // ==================== 任务日志 ====================

    public PageResult<SysJobLog> pageLog(long pageNum, long pageSize, Long jobId, String jobName, Integer status) {
        IPage<SysJobLog> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<SysJobLog> wrapper = new LambdaQueryWrapper<SysJobLog>()
                .eq(jobId != null, SysJobLog::getJobId, jobId)
                .like(StrUtil.isNotBlank(jobName), SysJobLog::getJobName, jobName)
                .eq(status != null, SysJobLog::getStatus, status)
                .orderByDesc(SysJobLog::getStartTime);
        return PageResult.of(jobLogMapper.selectPage(page, wrapper));
    }

    @Transactional(rollbackFor = Exception.class)
    public int deleteLog(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return 0;
        }
        return jobLogMapper.deleteBatchIds(ids);
    }

    /**
     * 分布式锁执行器：多实例部署时保证同一时刻只有一个实例跑任务。
     *
     * <p>锁的 value 用随机串，释放时先比对再删（Lua），
     * 防止"自己超时后被别人抢到锁，又去把别人的锁删掉"。
     */
    public void runWithLock(String jobKey, Runnable task, long timeoutSeconds) {
        String lockKey = String.format(Constants.KEY_JOB_LOCK, jobKey);
        String requestId = UUID.randomUUID().toString();
        if (!redisUtils.tryLock(lockKey, requestId, timeoutSeconds, java.util.concurrent.TimeUnit.SECONDS)) {
            log.info("任务 {} 已被其它实例占用，本次跳过", jobKey);
            return;
        }
        try {
            task.run();
        } finally {
            redisUtils.unlock(lockKey, requestId);
        }
    }

    private void validateCron(String cron) {
        if (StrUtil.isBlank(cron)) {
            throw new BaseException(ErrorCode.JOB_CRON_INVALID);
        }
        try {
            CronExpression.parse(cron);
        } catch (Exception e) {
            throw new BaseException(ErrorCode.JOB_CRON_INVALID, "cron 表达式不合法：" + cron);
        }
    }
}
