package com.lizp.tone.module.auth.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Set;

/** 当前登录用户信息 */
@Data
@Builder
@Schema(description = "当前登录用户信息")
public class UserInfoVO {

    @Schema(description = "用户ID")
    private Long userId;

    @Schema(description = "账号")
    private String username;

    @Schema(description = "昵称")
    private String nickname;

    @Schema(description = "真实姓名")
    private String realName;

    @Schema(description = "头像")
    private String avatar;

    @Schema(description = "邮箱")
    private String email;

    @Schema(description = "手机号")
    private String phone;

    @Schema(description = "性别：0男 1女 2未知")
    private Integer sex;

    @Schema(description = "部门ID")
    private Long deptId;

    @Schema(description = "部门名称")
    private String deptName;

    @Schema(description = "是否超级管理员")
    private Boolean superAdmin;

    @Schema(description = "角色编码集合")
    private List<String> roles;

    @Schema(description = "权限标识集合，前端做按钮级控制")
    private Set<String> perms;

    @Schema(description = "是否需要强制修改初始密码")
    private Boolean mustChangePassword;
}
