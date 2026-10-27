package com.lizp.tone.module.auth.vo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

/** 登录/刷新响应 */
@Data
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(description = "登录响应")
public class LoginVO {

    @Schema(description = "token 类型")
    private String tokenType = "Bearer";

    @Schema(description = "accessToken")
    private String accessToken;

    @Schema(description = "refreshToken")
    private String refreshToken;

    @Schema(description = "accessToken 有效期（秒）")
    private long expiresIn;

    @Schema(description = "用户ID")
    private Long userId;

    @Schema(description = "账号")
    private String username;

    @Schema(description = "昵称")
    private String nickname;

    @Schema(description = "头像")
    private String avatar;

    /** 前端据此跳转强制改密页 */
    @Schema(description = "是否需要强制修改初始密码")
    private Boolean mustChangePassword;
}
