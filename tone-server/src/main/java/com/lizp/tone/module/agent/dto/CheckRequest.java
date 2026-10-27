package com.lizp.tone.module.agent.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 检查请求。
 */
@Data
@Schema(description = "检查请求")
public class CheckRequest {

    @NotBlank(message = "请输入要检查的文字")
    @Schema(description = "待检查文本")
    private String text;

    @NotBlank(message = "请选择场景")
    @Schema(description = "场景：workplace/social/relationship/moments/service")
    private String scene;
}
