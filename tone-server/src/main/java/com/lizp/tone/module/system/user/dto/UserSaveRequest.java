package com.lizp.tone.module.system.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/** 新增/修改用户请求 */
@Data
@Schema(description = "用户新增/修改请求")
public class UserSaveRequest {

    @Schema(description = "用户ID，新增时不传")
    private Long id;

    @Size(min = 2, max = 30, message = "账号长度需在 2 到 30 位之间")
    @Pattern(regexp = "^[a-zA-Z0-9_]+$", message = "账号只能包含字母、数字和下划线")
    @Schema(description = "登录账号")
    private String username;

    @Schema(description = "昵称")
    private String nickname;

    @Schema(description = "真实姓名")
    private String realName;

    @Schema(description = "部门ID")
    private Long deptId;

    @Schema(description = "邮箱")
    @Email(message = "邮箱格式不正确")
    private String email;

    // 允许空串：手机号非必填，前端不填时会传空字符串，纯 ^1[3-9]\d{9}$ 会把空值判为非法
    @Pattern(regexp = "^$|^1[3-9]\\d{9}$", message = "手机号格式不正确")
    @Schema(description = "手机号")
    private String phone;

    @Schema(description = "性别：0男 1女 2未知")
    private Integer sex;

    @Schema(description = "状态：0正常 1停用")
    private Integer status;

    @Schema(description = "头像")
    private String avatar;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "角色ID集合")
    private List<Long> roleIds;
}
