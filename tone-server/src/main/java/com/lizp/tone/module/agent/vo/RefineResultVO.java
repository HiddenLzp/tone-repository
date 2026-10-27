package com.lizp.tone.module.agent.vo;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 微调结果。
 */
@Schema(description = "微调结果")
public record RefineResultVO(
        @Schema(description = "检查记录 id") Long recordId,
        @Schema(description = "本轮微调后的文本") String text,
        @Schema(description = "剩余可用次数") int remain) {
}
