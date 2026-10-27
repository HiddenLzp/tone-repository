package com.lizp.tone.module.agent.service;

import cn.hutool.core.io.IoUtil;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 本地敏感词检测：双词库来源。
 *
 * <p>加载优先级：外部文件 ./config/sensitive-words.txt → classpath:sensitive-words.txt。
 * 之所以引入外部文件：classpath 在打好的 jar 里**不可写**，管理台「运营设置」保存的
 * 词库必须落到外部文件才能持久（重启/重新部署不丢）；classpath 版作为开箱默认。
 *
 * <p>双用途：1) 检查入口的硬拦截（与 msgSecCheck 并行，本地零延迟先挡一层）；
 * 2) Agent 循环里的 detect_sensitive_words 工具。
 * 保存/修改立即生效（saveWords 内部触发 reload），无需重启。
 */
@Slf4j
@Service
public class SensitiveWordService {

    /** 外部词库路径：工作目录 config/ 下（与 upload/ 同级的可写目录） */
    private static final Path EXTERNAL_FILE = Path.of("config", "sensitive-words.txt");

    private volatile List<String> words = List.of();

    @PostConstruct
    public void init() {
        reload();
    }

    public synchronized void reload() {
        // 外部文件存在则优先：运营保存过的词库永远盖过 jar 内默认
        if (Files.exists(EXTERNAL_FILE)) {
            try {
                this.words = Files.readAllLines(EXTERNAL_FILE, StandardCharsets.UTF_8).stream()
                        .map(String::trim)
                        .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                        .toList();
                log.info("敏感词库（外部 {}）加载完成：{} 个词", EXTERNAL_FILE, words.size());
                return;
            } catch (IOException e) {
                log.warn("外部敏感词文件读取失败，回退 classpath 默认", e);
            }
        }
        try (InputStream in = new ClassPathResource("sensitive-words.txt").getInputStream()) {
            String content = IoUtil.read(in, StandardCharsets.UTF_8);
            this.words = content.lines()
                    .map(String::trim)
                    .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                    .toList();
            log.info("敏感词库（classpath 默认）加载完成：{} 个词", words.size());
        } catch (IOException e) {
            log.warn("sensitive-words.txt 加载失败，敏感词检测将退化为空词库", e);
        }
    }

    /** 当前词库快照（管理台展示用；返回副本防外部修改） */
    public List<String> getWords() {
        return new ArrayList<>(words);
    }

    /**
     * 保存词库：写入外部文件（自动建 config 目录）并立即 reload。
     * 空行与 # 注释行被过滤；重复词去重（LinkedHashSet 保序去重足够）。
     */
    public synchronized void saveWords(List<String> newWords) throws IOException {
        List<String> cleaned = newWords == null ? List.of() : newWords.stream()
                .map(w -> w == null ? "" : w.trim())
                .filter(w -> !w.isEmpty() && !w.startsWith("#"))
                .distinct()
                .toList();
        Files.createDirectories(EXTERNAL_FILE.getParent());
        Files.write(EXTERNAL_FILE, cleaned, StandardCharsets.UTF_8);
        reload();
        log.info("敏感词库已保存（{} 个词）并即时生效", cleaned.size());
    }

    /** 返回命中的敏感词列表（去重） */
    public List<String> findHits(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        List<String> hits = new ArrayList<>();
        for (String word : words) {
            if (text.contains(word) && !hits.contains(word)) {
                hits.add(word);
            }
        }
        return hits;
    }

    public boolean containsSensitive(String text) {
        return !findHits(text).isEmpty();
    }
}
