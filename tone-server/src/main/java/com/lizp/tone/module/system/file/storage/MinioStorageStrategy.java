package com.lizp.tone.module.system.file.storage;

import com.lizp.tone.config.BaseProperties;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import lombok.extern.slf4j.Slf4j;

import java.io.ByteArrayInputStream;
import java.util.NoSuchElementException;

/**
 * MinIO 存储策略。
 *
 * <p>两个区域靠 key 前缀区分：public 直接用 {@code yyyy/MM/name}，
 * origin 统一加 {@code base.file.origin.key-prefix}（默认 origin/）。
 * 前缀本身不保密（bucket 可以整桶公开），真正的边界在于：
 * <b>页面永远只拿到 public 的 key</b>，origin 的 key 只在服务端签发签名链接时使用。
 *
 * <p>由 {@link MinioStorageConfiguration} 在 storage-type=minio 且 SDK 存在时创建；
 * 本类直接 import 了 minio 的类，因此不能标注 @Component——否则缺依赖时类扫描就会报错。
 */
@Slf4j
public class MinioStorageStrategy implements FileStorageStrategy {

    private final MinioClient client;
    private final BaseProperties properties;

    public MinioStorageStrategy(MinioClient client, BaseProperties properties) {
        this.client = client;
        this.properties = properties;
    }

    @Override
    public String type() {
        return "minio";
    }

    @Override
    public void save(StorageArea area, String relativeKey, byte[] data, String contentType) {
        String bucket = bucket();
        String key = objectKey(area, relativeKey);
        try (ByteArrayInputStream in = new ByteArrayInputStream(data)) {
            client.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(key)
                    .stream(in, data.length, -1)
                    .contentType(contentType == null ? "application/octet-stream" : contentType)
                    .build());
        } catch (Exception e) {
            log.error("MinIO 写入失败 bucket={} key={}", bucket, key, e);
            throw new IllegalStateException("MinIO 写入失败：" + key, e);
        }
    }

    @Override
    public byte[] read(StorageArea area, String relativeKey) {
        String key = objectKey(area, relativeKey);
        try (var stream = client.getObject(GetObjectArgs.builder().bucket(bucket()).object(key).build())) {
            return stream.readAllBytes();
        } catch (Exception e) {
            // 不把 SDK 异常一股脑抛出去：里面可能带 AccessKey 之类的敏感信息
            log.warn("MinIO 读取失败 key={} msg={}", key, e.getMessage());
            throw new NoSuchElementException("文件不存在或读取失败：" + key);
        }
    }

    @Override
    public void delete(StorageArea area, String relativeKey) {
        String key = objectKey(area, relativeKey);
        try {
            client.removeObject(RemoveObjectArgs.builder().bucket(bucket()).object(key).build());
        } catch (Exception e) {
            log.warn("MinIO 删除失败 key={}", key, e);
        }
    }

    private String bucket() {
        return properties.getFile().getMinio().getBucket();
    }

    private String objectKey(StorageArea area, String relativeKey) {
        String prefix = properties.getFile().getOrigin().getKeyPrefix();
        return area == StorageArea.ORIGIN ? prefix + "/" + relativeKey : relativeKey;
    }
}
