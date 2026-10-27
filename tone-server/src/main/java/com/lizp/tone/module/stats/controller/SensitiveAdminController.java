package com.lizp.tone.module.stats.controller;

import com.lizp.tone.common.R;
import com.lizp.tone.module.agent.service.SensitiveWordService;
import com.lizp.tone.security.authz.RequiresPermissions;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 敏感词库管理：查看 / 保存（立即生效）/ 手动重载。
 *
 * <p>保存落外部文件 ./config/sensitive-words.txt（jar 内 classpath 不可写，见
 * SensitiveWordService 的注释），保存后内部触发 reload，检查链路即时用新词库。
 */
@Tag(name = "敏感词库")
@RestController
@RequestMapping("/agent/sensitive")
@RequiredArgsConstructor
public class SensitiveAdminController {

    private final SensitiveWordService sensitiveWordService;

    @Data
    public static class WordsVO {
        private List<String> words;
        /** 是否来自外部文件（运营保存过）——展示提示用 */
        private boolean external;
    }

    @GetMapping
    @RequiresPermissions("agent:settings:view")
    @Operation(summary = "当前词库列表")
    public R<WordsVO> list() {
        WordsVO vo = new WordsVO();
        vo.setWords(sensitiveWordService.getWords());
        vo.setExternal(java.nio.file.Files.exists(java.nio.file.Path.of("config", "sensitive-words.txt")));
        return R.ok(vo);
    }

    @PutMapping
    @RequiresPermissions("agent:settings:edit")
    @Operation(summary = "保存词库（每行一个词，支持 # 注释；保存即生效，写入外部文件持久）")
    public R<WordsVO> save(@RequestBody WordsVO body) {
        try {
            sensitiveWordService.saveWords(body.getWords());
        } catch (java.io.IOException e) {
            return R.fail("词库文件写入失败：" + e.getMessage());
        }
        return list();
    }

    @PostMapping("/reload")
    @RequiresPermissions("agent:settings:edit")
    @Operation(summary = "手动重载词库（直接改服务器上的文件后调用；页面保存不需要）")
    public R<String> reload() {
        sensitiveWordService.reload();
        return R.ok("已重新加载");
    }
}
