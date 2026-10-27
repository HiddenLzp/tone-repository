package com.lizp.tone.module.agent.vo;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 语气问题。
 */
@Schema(description = "语气问题")
public record IssueVO(
        @Schema(description = "类型：politeness/clarity/scene/sensitive") String type,
        @Schema(description = "严重度：high/mid/low") String level,
        @Schema(description = "原文片段") String quote,
        @Schema(description = "修改建议") String suggestion) {
}
