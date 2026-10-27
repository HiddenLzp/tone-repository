package com.lizp.tone.module.agent.service;

import cn.hutool.core.io.IoUtil;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * 场景规则库：从 classpath 的 scene-rules.yml 加载，check_scene_rules 工具的数据源。
 */
@Slf4j
@Service
public class SceneRuleService {

    private Map<String, SceneRule> ruleMap = Map.of();

    @SuppressWarnings("unchecked")
    @PostConstruct
    public void load() {
        Yaml yaml = new Yaml();
        try (InputStream in = new ClassPathResource("scene-rules.yml").getInputStream()) {
            Map<String, Object> root = yaml.load(IoUtil.read(in, StandardCharsets.UTF_8));
            this.ruleMap = root.entrySet().stream()
                    .collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, e -> {
                        Map<String, Object> v = (Map<String, Object>) e.getValue();
                        return new SceneRule((String) v.get("label"), (List<String>) v.get("rules"));
                    }));
            log.info("场景规则加载完成：{} 个场景", ruleMap.size());
        } catch (IOException e) {
            log.warn("scene-rules.yml 加载失败，场景校验将退化为空规则", e);
        }
    }

    public SceneRule getRule(String scene) {
        return ruleMap.get(scene);
    }

    /** 未登记场景返回通用规则（空实现，交给 LLM 常识判断） */
    public String rulesText(String scene) {
        SceneRule rule = getRule(scene);
        if (rule == null) {
            return "（该场景暂无专项规则，按通用沟通礼仪判断）";
        }
        return String.join("\n", rule.rules());
    }

    public record SceneRule(String label, List<String> rules) {
    }
}
