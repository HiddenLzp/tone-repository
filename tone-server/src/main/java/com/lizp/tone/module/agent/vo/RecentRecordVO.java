package com.lizp.tone.module.agent.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 小程序端"最近记录"条目：字段与前端本地历史条目对齐（recordId/originalText/...），
 * 这样首页卡片、结果页可以在「本地缓存」与「后端记录」之间无缝互换。
 *
 * @param recordId     检查记录 id
 * @param scene        场景
 * @param originalText 原文
 * @param scores       评分（JSON 已解析）
 * @param issues       问题列表（JSON 已解析）
 * @param versions     改写版本（JSON 已解析）
 * @param finalText    最终采用文本（可能为空）
 * @param time         创建时间（epoch 毫秒，前端 formatTime 直接用）
 * @param status       done/blocked/error
 */
@Schema(description = "最近记录条目")
public record RecentRecordVO(
        @Schema(description = "检查记录 id") Long recordId,
        @Schema(description = "场景") String scene,
        @Schema(description = "原文") String originalText,
        @Schema(description = "评分") Object scores,
        @Schema(description = "问题列表") List<IssueVO> issues,
        @Schema(description = "改写版本") List<VersionVO> versions,
        @Schema(description = "最终采用文本") String finalText,
        @Schema(description = "创建时间（epoch 毫秒）") Long time,
        @Schema(description = "状态") String status) {
}
