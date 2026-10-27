package com.lizp.tone.module.system.file.storage;

import com.lizp.tone.config.BaseProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 阿里云 OSS 存储的装配条件，说明见 {@link MinioStorageConfiguration}。
 *
 * <p>{@code destroyMethod} 会在容器关闭时回调 {@link OssStorageStrategy#shutdown()}，
 * 释放 SDK 内部的 HTTP 连接池——不关的话，热部署 / 反复重启会堆积连接。
 */
@Slf4j
@Configuration
@ConditionalOnClass(name = "com.aliyun.oss.OSSClientBuilder")
@ConditionalOnProperty(prefix = "base.file", name = "storage-type", havingValue = "oss")
public class OssStorageConfiguration {

    @Bean(destroyMethod = "shutdown")
    public OssStorageStrategy ossStorageStrategy(BaseProperties properties) {
        BaseProperties.FileProps.Oss cfg = properties.getFile().getOss();
        if (cfg.getEndpoint() == null || cfg.getEndpoint().isBlank()
                || cfg.getAccessKeyId() == null || cfg.getAccessKeyId().isBlank()) {
            throw new IllegalStateException(
                    "storage-type=oss 但未配置 base.file.oss.endpoint / access-key-id，请检查 application.yml");
        }
        log.info("文件存储：阿里云 OSS endpoint={} bucket={}", cfg.getEndpoint(), cfg.getBucketName());
        return new OssStorageStrategy(OssStorageStrategy.buildClient(properties), properties);
    }
}
