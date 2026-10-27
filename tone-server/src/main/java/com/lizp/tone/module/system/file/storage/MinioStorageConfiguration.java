package com.lizp.tone.module.system.file.storage;

import com.lizp.tone.config.BaseProperties;
import io.minio.MinioClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MinIO 存储的装配条件。
 *
 * <ul>
 *   <li>{@code @ConditionalOnClass} 用<b>类名字符串</b>而不是 {@code MinioClient.class}：
 *       写 Class 字面量会强制加载这个类，缺依赖时注解求值阶段就炸；字符串形式由 ASM 判断，安全。</li>
 *   <li>{@code @ConditionalOnProperty} 保证只有 {@code storage-type=minio} 时才建 bean。</li>
 * </ul>
 *
 * <p>两个条件都不满足时不注册任何东西，{@link FileStorageFactory} 会在启动阶段报错提示缺依赖，
 * 而不是等到某个上传请求才发现。
 */
@Slf4j
@Configuration
@ConditionalOnClass(name = "io.minio.MinioClient")
@ConditionalOnProperty(prefix = "base.file", name = "storage-type", havingValue = "minio")
public class MinioStorageConfiguration {

    @Bean
    public MinioStorageStrategy minioStorageStrategy(BaseProperties properties) {
        BaseProperties.FileProps.Minio cfg = properties.getFile().getMinio();
        if (cfg.getEndpoint() == null || cfg.getEndpoint().isBlank()
                || cfg.getAccessKey() == null || cfg.getAccessKey().isBlank()) {
            // 快速失败好过后面在某个上传请求里抛 NPE
            throw new IllegalStateException(
                    "storage-type=minio 但未配置 base.file.minio.endpoint / access-key，请检查 application.yml");
        }
        MinioClient client = MinioClient.builder()
                .endpoint(cfg.getEndpoint())
                .credentials(cfg.getAccessKey(), cfg.getSecretKey())
                .build();
        log.info("文件存储：MinIO endpoint={} bucket={}", cfg.getEndpoint(), cfg.getBucket());
        return new MinioStorageStrategy(client, properties);
    }
}
