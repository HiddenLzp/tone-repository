package com.lizp.tone.module.job;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.config.CronTask;
import org.springframework.scheduling.config.ScheduledTask;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 动态定时任务注册器。
 *
 * <p>用 {@code SchedulingConfigurer} 拿到 {@link ScheduledTaskRegistrar}，
 * 之后就能在运行时增删任务——改 cron 不用重启应用。
 *
 * <p>没有引入 Quartz 的原因：Quartz 需要额外的十几张表和一个调度线程池，
 * 对于"改改 cron、看看执行记录"这种需求太重了。真到了需要分布式调度、失败重试、
 * 任务编排的阶段，再把这里换成 XXL-Job / PowerJob 即可，业务层接口不用变。
 */
@Slf4j
@Component
public class CronTaskRegistrar implements org.springframework.scheduling.annotation.SchedulingConfigurer {

    private final Map<Long, ScheduledTask> scheduledTasks = new ConcurrentHashMap<>();

    private volatile ScheduledTaskRegistrar taskRegistrar;

    @Override
    public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
        this.taskRegistrar = taskRegistrar;
    }

    /** 注册（或更新）一个 cron 任务 */
    public void addCronTask(Long jobId, Runnable task, String cron) {
        removeCronTask(jobId);
        ScheduledTaskRegistrar registrar = this.taskRegistrar;
        if (registrar == null) {
            log.warn("任务注册器尚未就绪，任务 {} 稍后重试", jobId);
            return;
        }
        CronTask cronTask = new CronTask(task, new CronTrigger(cron));
        ScheduledTask scheduledTask = registrar.scheduleCronTask(cronTask);
        scheduledTasks.put(jobId, scheduledTask);
        log.info("注册定时任务 jobId={} cron={}", jobId, cron);
    }

    public void removeCronTask(Long jobId) {
        ScheduledTask scheduledTask = scheduledTasks.remove(jobId);
        if (scheduledTask != null) {
            scheduledTask.cancel();
        }
    }

    public boolean hasTask(Long jobId) {
        return scheduledTasks.containsKey(jobId);
    }
}
