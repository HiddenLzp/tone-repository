package com.lizp.tone.module.agent.vo;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 评分。politeness/professionalism 0-100，emotion -100~100（负值=负面倾向）。
 */
@Schema(description = "语气评分")
public record ScoreVO(
        @Schema(description = "礼貌度 0-100") int politeness,
        @Schema(description = "专业度 0-100") int professionalism,
        @Schema(description = "情感倾向 -100~100") int emotion) {
}
