package com.lizp.tone.module.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/** 刷新 token 请求 */
@Schema(description = "刷新令牌请求")
public record RefreshRequest(
        @NotBlank(message = "refreshToken 不能为空")
        @Schema(description = "refreshToken", requiredMode = Schema.RequiredMode.REQUIRED)
        String refreshToken
) {
}
