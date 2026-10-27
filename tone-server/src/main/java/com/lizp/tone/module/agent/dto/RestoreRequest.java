package com.lizp.tone.module.agent.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 版本回退请求。
 */
@Data
@Schema(description = "版本回退请求")
public class RestoreRequest {

    @NotNull(message = "缺少记录 id")
    @Schema(description = "检查记录 id")
    private Long recordId;

    @NotBlank(message = "缺少要采用的文本")
    @Schema(description = "要采用的版本文本")
    private String text;
}
