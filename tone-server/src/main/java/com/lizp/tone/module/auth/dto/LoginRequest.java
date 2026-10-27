package com.lizp.tone.module.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** 登录请求 */
@Data
@Schema(description = "登录请求")
public class LoginRequest {

    @NotBlank(message = "账号不能为空")
    @Schema(description = "登录账号", requiredMode = Schema.RequiredMode.REQUIRED)
    private String username;

    @NotBlank(message = "密码不能为空")
    @Schema(description = "密码", requiredMode = Schema.RequiredMode.REQUIRED)
    private String password;

    @Schema(description = "验证码ID（开启验证码时必填）")
    private String captchaKey;

    @Schema(description = "验证码值（开启验证码时必填）")
    private String captchaCode;

    @Schema(description = "客户端类型：web/app/h5")
    private String clientType = "web";

    @Schema(description = "设备ID")
    private String deviceId;
}
