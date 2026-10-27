package com.lizp.tone.module.agent.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 检查结果。
 *
 * @param recordId     检查记录 id，微调时回传
 * @param blocked      是否被敏感词硬拦截
 * @param hits         命中的敏感词
 * @param scores       三项评分
 * @param issues       问题列表
 * @param versions     三个改写版本
 * @param rounds       Agent 实际执行轮数
 * @param scene        场景
 */
@Schema(description = "检查结果")
public record CheckResultVO(
        @Schema(description = "检查记录 id") Long recordId,
        @Schema(description = "是否被敏感词拦截") boolean blocked,
        @Schema(description = "命中的敏感词") List<String> hits,
        @Schema(description = "评分") ScoreVO scores,
        @Schema(description = "问题列表") List<IssueVO> issues,
        @Schema(description = "改写版本") List<VersionVO> versions,
        @Schema(description = "Agent 执行轮数") int rounds,
        @Schema(description = "场景") String scene) {
}
