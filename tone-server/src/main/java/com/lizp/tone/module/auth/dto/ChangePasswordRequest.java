package com.lizp.tone.module.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 修改密码请求 */
@Data
@Schema(description = "修改密码请求")
public class ChangePasswordRequest {

    /**
     * 原密码。首次登录强制改密（库中 first_login_flag = 1）时可不传：
     * 用户刚用初始密码登录成功，再要求输一遍原密码没有意义。
     *
     * <p>是否豁免只能由服务端读库判定，这里不能加 skipOldPassword 之类的开关字段，
     * 否则客户端传个 true 就能绕过原密码校验。
     */
    @Schema(description = "原密码；首次登录强制改密时可不传")
    private String oldPassword;

    @NotBlank(message = "新密码不能为空")
    @Size(min = 6, max = 32, message = "密码长度需在 6 到 32 位之间")
    @Schema(description = "新密码", requiredMode = Schema.RequiredMode.REQUIRED)
    private String newPassword;
}
