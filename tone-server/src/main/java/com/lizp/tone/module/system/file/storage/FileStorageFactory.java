package com.lizp.tone.module.system.file.storage;

import com.lizp.tone.config.BaseProperties;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 按配置挑出当前生效的存储策略。
 *
 * <p>三种实现都用 {@code @ConditionalOn*} 做了条件装配，
 * 所以正常情况下容器里只有选中的那一个。这里做 Map 式解析而不是直接注入单个 bean，
 * 好处是：配错类型时能在<b>启动阶段</b>给出「配的 X，现在只有 Y」的明确提示，
 * 而不是等某个上传请求进来才抛 NoSuchBeanDefinitionException。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FileStorageFactory {

    private final BaseProperties properties;
    private final List<FileStorageStrategy> strategies;

    @PostConstruct
    public void check() {
        String configured = properties.getFile().getStorageType();
        if (strategies.isEmpty()) {
            throw new IllegalStateException(
                    "没有任何文件存储策略可用，请检查 base.file.storage-type=" + configured + " 与 pom 中对应的 SDK 依赖");
        }
        // 立即解析一次：故意让配置错误在启动阶段暴露
        FileStorageStrategy picked = resolve();
        log.info("文件存储策略：{}（可选：{}）",
                picked.type(),
                strategies.stream().map(FileStorageStrategy::type).collect(Collectors.joining(",")));
    }

    /**
     * 取得当前配置的策略。
     *
     * @throws IllegalStateException 配置的类型没有对应实现（通常是类型写错或 SDK 依赖没引）
     */
    public FileStorageStrategy resolve() {
        String configured = properties.getFile().getStorageType();
        return strategies.stream()
                .filter(s -> s.type().equalsIgnoreCase(configured))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(String.format(
                        "未找到存储类型 '%s' 的实现。可用类型：%s。"
                                + "若目标为 minio / oss，请确认 pom 已引入对应 SDK（当前为 optional 依赖）",
                        configured,
                        strategies.stream().map(FileStorageStrategy::type).collect(Collectors.joining(",")))));
    }
}
