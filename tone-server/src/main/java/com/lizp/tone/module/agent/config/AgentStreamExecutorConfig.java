package com.lizp.tone.module.agent.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Agent SSE 流式任务的专用线程池。
 *
 * <p>为什么独立建池而不是复用模板的 taskExecutor / Tomcat 工作线程：
 * <ul>
 *   <li>SSE 接口在 HTTP 线程上只负责"挂起连接"，Agent 执行要挪到后台线程跑
 *       （一次完整检查 60 秒+），不挪的话 Tomcat 200 个工作线程会被几个并发检查拖垮；</li>
 *   <li>也不能混用模板的 taskExecutor：那是给操作日志等短任务的，Agent 任务长达分钟级，
 *       混用会互相饿死；</li>
 *   <li>核心 4 线程 + 队列 8：个人小程序量级下并发检查极少，超出排队（CallerRuns 兜底
 *       让极端洪峰退化为同步执行也不至于拒绝）。</li>
 * </ul>
 */
@Configuration
public class AgentStreamExecutorConfig {

    @Bean("agentStreamExecutor")
    public ExecutorService agentStreamExecutor() {
        AtomicInteger seq = new AtomicInteger();
        ThreadFactory factory = r -> {
            Thread t = new Thread(r, "agent-stream-" + seq.incrementAndGet());
            // Agent 线程里只是发起 HTTP 调用并等待，非 CPU 密集；daemon 保证应用退出不被拖住
            t.setDaemon(true);
            return t;
        };
        return new ThreadPoolExecutor(
                4, 8,
                60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(8),
                factory,
                new ThreadPoolExecutor.CallerRunsPolicy());
    }
}
