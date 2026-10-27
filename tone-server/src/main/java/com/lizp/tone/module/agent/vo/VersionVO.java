package com.lizp.tone.module.agent.vo;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 改写版本。
 */
@Schema(description = "改写版本")
public record VersionVO(
        @Schema(description = "风格名：委婉礼貌/专业得体/温和亲近") String style,
        @Schema(description = "改写文本") String text) {
}
