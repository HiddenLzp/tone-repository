package com.lizp.tone.module.system.file.storage;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.model.OSSObject;
import com.lizp.tone.config.BaseProperties;
import lombok.extern.slf4j.Slf4j;

import java.io.ByteArrayInputStream;
import java.util.NoSuchElementException;

/**
 * 阿里云 OSS 存储策略。
 *
 * <p>与 {@link MinioStorageStrategy} 同一种思路：public / origin 靠 key 前缀区分，
 * 页面只拿 public 那份；origin 的 key 不出现在任何对外响应里，
 * 「查看原图」走的是服务端签发的临时链接。
 *
 * <p>由 {@link OssStorageConfiguration} 在 storage-type=oss 且 SDK 存在时创建；
 * 本类直接 import 了 OSS SDK，因此不能标注 @Component（缺依赖时类扫描会失败）。
 */
@Slf4j
public class OssStorageStrategy implements FileStorageStrategy {

    private final OSS client;
    private final BaseProperties properties;

    public OssStorageStrategy(OSS client, BaseProperties properties) {
        this.client = client;
        this.properties = properties;
    }

    @Override
    public String type() {
        return "oss";
    }

    @Override
    public void save(StorageArea area, String relativeKey, byte[] data, String contentType) {
        String bucket = bucket();
        String key = objectKey(area, relativeKey);
        var metadata = new com.aliyun.oss.model.ObjectMetadata();
        metadata.setContentLength(data.length);
        if (contentType != null) {
            metadata.setContentType(contentType);
        }
        try (ByteArrayInputStream in = new ByteArrayInputStream(data)) {
            client.putObject(bucket, key, in, metadata);
        } catch (Exception e) {
            // 不要把 SDK 异常原样抛出去：异常消息里可能带 Endpoint / AccessKey 信息
            log.error("OSS 写入失败 bucket={} key={} msg={}", bucket, key, e.getMessage());
            throw new IllegalStateException("OSS 写入失败：" + key);
        }
    }

    @Override
    public byte[] read(StorageArea area, String relativeKey) {
        String key = objectKey(area, relativeKey);
        try (OSSObject obj = client.getObject(bucket(), key)) {
            if (obj == null) {
                throw new NoSuchElementException("文件不存在：" + key);
            }
            return obj.getObjectContent().readAllBytes();
        } catch (java.io.IOException e) {
            // 不要把 SDK 异常原样抛出去：异常消息里可能带 Endpoint / AccessKey 信息
            log.warn("OSS 读取失败 key={} msg={}", key, e.getMessage());
            throw new NoSuchElementException("文件不存在或读取失败：" + key);
        }
    }

    @Override
    public void delete(StorageArea area, String relativeKey) {
        String key = objectKey(area, relativeKey);
        try {
            client.deleteObject(bucket(), key);
        } catch (Exception e) {
            log.warn("OSS 删除失败 key={}", key, e);
        }
    }

    /** 连接不再使用时由 Spring 回调关闭（OSSClient 内部持有 HTTP 连接池，不关会泄漏） */
    public void shutdown() {
        try {
            client.shutdown();
        } catch (Exception e) {
            log.warn("OSS 客户端关闭异常", e);
        }
    }

    private String bucket() {
        return properties.getFile().getOss().getBucketName();
    }

    private String objectKey(StorageArea area, String relativeKey) {
        String prefix = properties.getFile().getOrigin().getKeyPrefix();
        return area == StorageArea.ORIGIN ? prefix + "/" + relativeKey : relativeKey;
    }

    /** 构造 OSSClient：抽出来是为了让装配类不关心 SDK 细节 */
    public static OSS buildClient(BaseProperties properties) {
        BaseProperties.FileProps.Oss cfg = properties.getFile().getOss();
        return new OSSClientBuilder().build(cfg.getEndpoint(), cfg.getAccessKeyId(), cfg.getAccessKeySecret());
    }
}
