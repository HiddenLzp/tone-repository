package com.lizp.tone.module.stats.controller;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lizp.tone.common.PageResult;
import com.lizp.tone.common.R;
import com.lizp.tone.module.agent.entity.AppCheckMessage;
import com.lizp.tone.module.agent.entity.AppCheckRecord;
import com.lizp.tone.module.agent.mapper.AppCheckMessageMapper;
import com.lizp.tone.module.agent.mapper.AppCheckRecordMapper;
import com.lizp.tone.security.authz.RequiresPermissions;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 检查记录管理（管理台）：分页 + 详情（评分/问题/版本 JSON 解析后返回）。
 */
@Tag(name = "检查记录管理")
@RestController
@RequestMapping("/agent/records")
@RequiredArgsConstructor
public class CheckRecordAdminController {

    private final AppCheckRecordMapper recordMapper;
    private final AppCheckMessageMapper messageMapper;
    private final ObjectMapper objectMapper;

    @Data
    public static class RecordVO {
        private Long id;
        private String openId;
        private String scene;
        private String originalText;
        private Object scores;
        private Object issues;
        private Object versions;
        private Integer selectedVersion;
        private String finalText;
        private Integer rounds;
        private Long promptTokens;
        private Long completionTokens;
        private String status;
        private String createTime;
    }

    @GetMapping
    @RequiresPermissions("agent:records:list")
    @Operation(summary = "检查记录分页")
    public R<PageResult<RecordVO>> page(@RequestParam(defaultValue = "1") long pageNum,
                                        @RequestParam(defaultValue = "10") long pageSize,
                                        @RequestParam(required = false) String scene,
                                        @RequestParam(required = false) String openId,
                                        @RequestParam(required = false) String status,
                                        @RequestParam(required = false) java.time.LocalDateTime beginTime,
                                        @RequestParam(required = false) java.time.LocalDateTime endTime) {
        Page<AppCheckRecord> page = recordMapper.selectPage(new Page<>(pageNum, pageSize),
                Wrappers.<AppCheckRecord>lambdaQuery()
                        .eq(StringUtils.hasText(scene), AppCheckRecord::getScene, scene)
                        .like(StringUtils.hasText(openId), AppCheckRecord::getOpenId, openId)
                        .eq(StringUtils.hasText(status), AppCheckRecord::getStatus, status)
                        .ge(beginTime != null, AppCheckRecord::getCreateTime, beginTime)
                        .le(endTime != null, AppCheckRecord::getCreateTime, endTime)
                        .orderByDesc(AppCheckRecord::getId));
        return R.ok(PageResult.of(page, this::toVO));
    }

    @GetMapping("/{id}")
    @RequiresPermissions("agent:records:query")
    @Operation(summary = "记录详情（含对话明细）")
    public R<Map<String, Object>> detail(@PathVariable Long id) {
        AppCheckRecord record = recordMapper.selectById(id);
        if (record == null) {
            return R.fail("记录不存在");
        }
        List<AppCheckMessage> messages = messageMapper.selectList(
                Wrappers.<AppCheckMessage>lambdaQuery()
                        .eq(AppCheckMessage::getRecordId, id)
                        .orderByAsc(AppCheckMessage::getId));
        Map<String, Object> data = new HashMap<>();
        data.put("record", toVO(record));
        data.put("messages", messages);
        return R.ok(data);
    }

    private RecordVO toVO(AppCheckRecord record) {
        RecordVO vo = new RecordVO();
        vo.setId(record.getId());
        vo.setOpenId(record.getOpenId());
        vo.setScene(record.getScene());
        vo.setOriginalText(record.getOriginalText());
        vo.setScores(parse(record.getScores()));
        vo.setIssues(parse(record.getIssues()));
        vo.setVersions(parse(record.getVersions()));
        vo.setSelectedVersion(record.getSelectedVersion());
        vo.setFinalText(record.getFinalText());
        vo.setRounds(record.getRounds());
        vo.setPromptTokens(record.getPromptTokens());
        vo.setCompletionTokens(record.getCompletionTokens());
        vo.setStatus(record.getStatus());
        vo.setCreateTime(record.getCreateTime() == null ? null : record.getCreateTime().toString());
        return vo;
    }

    /**
     * JSON 字符串还原成 Map/List。
     * Boot 4 的 Web 层用 Jackson 3，Jackson 2 的 JsonNode 会被当成普通 Bean 序列化出一堆
     * "array/nodeType" 布尔属性，所以这里必须转成普通容器对象再交给响应序列化。
     */
    private Object parse(String json) {
        if (!StringUtils.hasText(json)) {
            return null;
        }
        try {
            return objectMapper.readValue(json, Object.class);
        } catch (Exception e) {
            return null;
        }
    }
}
