package com.lizp.tone.module.job.task;

import com.lizp.tone.module.job.SysJobService;
import com.lizp.tone.module.system.online.SysOnlineService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** 在线会话清理任务：清掉已登出/已强退且超过保留期的令牌记录 */
@Slf4j
@Component("sessionCleanTask")
@RequiredArgsConstructor
public class SessionCleanTask {

    /** 过期会话记录保留天数：留一段时间便于事后审计 */
    private static final int RETAIN_DAYS = 30;

    private final SysOnlineService onlineService;
    private final SysJobService jobService;

    /** 对应 sys_job 里的 invoke_target：sessionCleanTask.cleanExpiredSession() */
    public void cleanExpiredSession() {
        jobService.runWithLock("cleanSession", () -> {
            int count = onlineService.cleanExpired(RETAIN_DAYS);
            log.info("过期会话清理完成，共 {} 条", count);
        }, 10 * 60);
    }
}
