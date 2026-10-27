package com.lizp.tone.module.agent.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 微调请求。
 */
@Data
@Schema(description = "微调请求")
public class RefineRequest {

    @NotNull(message = "缺少记录 id")
    @Schema(description = "检查记录 id")
    private Long recordId;

    @NotBlank(message = "请输入微调要求")
    @Schema(description = "微调指令，如：再委婉一点")
    private String instruction;
}
