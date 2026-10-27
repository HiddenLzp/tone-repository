package com.lizp.tone.module.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 个人中心修改资料请求。
 *
 * <p>这里只放允许账号本人修改的字段，是刻意的白名单：
 * 不能图省事直接复用 {@code UserSaveRequest} 然后靠前端不传敏感字段——
 * 请求体是用户可控的，只要 DTO 里有 deptId / roleIds / status / username，
 * 用户改个 JSON 就能给自己换部门、加角色、改账号（等于提权）。
 * 所以另建一个 DTO，字段收窄到「个人资料」这一件事上。
 */
@Data
@Schema(description = "个人资料修改请求")
public class ProfileUpdateRequest {

    @NotBlank(message = "昵称不能为空")
    @Size(max = 30, message = "昵称长度不能超过 30 位")
    @Schema(description = "昵称")
    private String nickname;

    @Size(max = 50, message = "真实姓名长度不能超过 50 位")
    @Schema(description = "真实姓名")
    private String realName;

    @Email(message = "邮箱格式不正确")
    @Schema(description = "邮箱")
    private String email;

    // 与 UserSaveRequest 保持一致：允许空串，手机号非必填
    @Pattern(regexp = "^$|^1[3-9]\\d{9}$", message = "手机号格式不正确")
    @Schema(description = "手机号")
    private String phone;

    @Schema(description = "性别：0男 1女 2未知")
    private Integer sex;

    @Size(max = 255, message = "头像地址过长")
    @Schema(description = "头像URL，一般由 /auth/avatar 上传后回填，也可直接传地址")
    private String avatar;
}
