package com.lizp.tone.module.system.file.storage;

import com.lizp.tone.config.BaseProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.NoSuchElementException;

/**
 * 本地磁盘存储（默认实现）。
 *
 * <p>两个区域对应两个<b>互不嵌套</b>的根目录：
 * <ul>
 *   <li>public → {@code base.file.local-path}（默认 ./upload），被 {@code WebMvcConfig} 映射成静态资源；</li>
 *   <li>origin → {@code base.file.origin.local-path}（默认 ./upload-origin），<b>不做任何映射</b>，
 *       因此没法通过 {@code /upload/...} 反推到原图。</li>
 * </ul>
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "base.file", name = "storage-type", havingValue = "local", matchIfMissing = true)
public class LocalStorageStrategy implements FileStorageStrategy {

    private final BaseProperties properties;

    public LocalStorageStrategy(BaseProperties properties) {
        this.properties = properties;
        checkAreaIsolated(properties.getFile());
    }

    /**
     * 原件区必须和展示区彻底分开。
     *
     * <p>如果有人图省事把 origin.local-path 配成 {@code ./upload/origin}，
     * 那原图就同时落在静态映射范围内——拼出路径照样能下载，压缩图白做了。
     * 这种配置错误不会报错、只会悄悄失效，所以必须在这儿硬拦。
     */
    private void checkAreaIsolated(BaseProperties.FileProps cfg) {
        Path publicRoot = Paths.get(cfg.getLocalPath()).toAbsolutePath().normalize();
        Path originRoot = Paths.get(cfg.getOrigin().getLocalPath()).toAbsolutePath().normalize();
        if (publicRoot.equals(originRoot) || originRoot.startsWith(publicRoot) || publicRoot.startsWith(originRoot)) {
            throw new IllegalStateException(String.format(
                    "原图目录与展示目录不能互相包含，否则原图会落入静态映射范围被直接下载：%s 与 %s",
                    publicRoot, originRoot));
        }
    }

    @Override
    public String type() {
        return "local";
    }

    @Override
    public void save(StorageArea area, String relativeKey, byte[] data, String contentType) {
        Path target = resolve(area, relativeKey);
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, data);
        } catch (IOException e) {
            log.error("本地文件写入失败 path={}", target, e);
            throw new IllegalStateException("本地文件写入失败：" + target, e);
        }
    }

    @Override
    public byte[] read(StorageArea area, String relativeKey) {
        Path target = resolve(area, relativeKey);
        if (!Files.isRegularFile(target)) {
            throw new NoSuchElementException("文件不存在：" + target);
        }
        try {
            return Files.readAllBytes(target);
        } catch (IOException e) {
            throw new IllegalStateException("本地文件读取失败：" + target, e);
        }
    }

    @Override
    public void delete(StorageArea area, String relativeKey) {
        Path target = resolve(area, relativeKey);
        try {
            Files.deleteIfExists(target);
        } catch (IOException e) {
            // 删除失败不构成业务失败（文件可能已被清理），只记录，避免把错抛给用户
            log.warn("本地文件删除失败 path={}", target, e);
        }
    }

    /**
     * 解析出绝对路径，并做目录穿越防护。
     *
     * <p>relativeKey 里若含 {@code ../}，normalize 之后会跑出根目录，
     * 必须在这里拦掉——否则上传接口就成了任意文件写入的口子。
     */
    private Path resolve(StorageArea area, String relativeKey) {
        BaseProperties.FileProps cfg = properties.getFile();
        String root = area == StorageArea.ORIGIN ? cfg.getOrigin().getLocalPath() : cfg.getLocalPath();
        Path base = Paths.get(root).toAbsolutePath().normalize();
        Path target = base.resolve(relativeKey).normalize();
        if (!target.startsWith(base)) {
            throw new IllegalArgumentException("非法文件路径：" + relativeKey);
        }
        return target;
    }
}
