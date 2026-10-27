package com.lizp.tone.config;

import com.alibaba.ttl.threadpool.TtlExecutors;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 异步线程池配置。
 *
 * <p>操作日志用 @Async 落库，因此线程池必须用 TTL 包装：
 * 否则异步线程里拿不到当前用户（UserContextHolder 为空），
 * 而且线程复用还会读到上一次请求残留的用户信息（串号）。
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean("taskExecutor")
    public ExecutorService taskExecutor() {
        ThreadFactory threadFactory = new ThreadFactory() {
            private final AtomicInteger counter = new AtomicInteger(1);

            @Override
            public Thread newThread(Runnable runnable) {
                Thread thread = new Thread(runnable);
                thread.setName("base-async-" + counter.getAndIncrement());
                return thread;
            }
        };
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                4,
                16,
                60L,
                TimeUnit.SECONDS,
                // 队列有界：日志写入量突增时宁可慢一点，也不能无限堆积把内存撑爆
                new LinkedBlockingQueue<>(5000),
                threadFactory,
                // 队列满了由提交任务的线程自己执行：起到背压作用，不至于丢任务
                new ThreadPoolExecutor.CallerRunsPolicy());
        return TtlExecutors.getTtlExecutorService(executor);
    }
}
