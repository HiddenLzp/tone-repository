package com.lizp.tone.module.system.user.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.lizp.tone.common.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/** 用户实体 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_user")
@Schema(description = "用户")
public class SysUser extends BaseEntity {

    @Schema(description = "登录账号")
    private String username;

    /**
     * BCrypt 密文。加 @JsonIgnore 是硬要求：查询用户列表的接口如果把这个字段带出去，
     * 等于把密码哈希泄露给前端。
     */
    @JsonIgnore
    @Schema(description = "密码", accessMode = Schema.AccessMode.READ_ONLY)
    private String password;

    @Schema(description = "昵称")
    private String nickname;

    @Schema(description = "真实姓名")
    private String realName;

    @Schema(description = "部门ID")
    private Long deptId;

    @Schema(description = "头像")
    private String avatar;

    @Schema(description = "邮箱")
    private String email;

    @Schema(description = "手机号")
    private String phone;

    @Schema(description = "性别：0男 1女 2未知")
    private Integer sex;

    @Schema(description = "用户类型：0系统用户")
    private Integer userType;

    @Schema(description = "状态：0正常 1停用")
    private Integer status;

    @Schema(description = "首次登录标记：1 表示首次登录需强制改密")
    private Integer firstLoginFlag;

    @Schema(description = "密码最后更新时间")
    private LocalDateTime pwdUpdateTime;

    @Schema(description = "连续登录失败次数")
    private Integer loginFailCount;

    @Schema(description = "锁定到期时间")
    private LocalDateTime lockTime;

    @Schema(description = "最后登录时间")
    private LocalDateTime lastLoginTime;

    @Schema(description = "最后登录IP")
    private String lastLoginIp;

    @Schema(description = "备注")
    private String remark;
}
