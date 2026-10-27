package com.lizp.tone.module.system.role.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * 用户与角色关联实体。
 *
 * <p>故意不继承 BaseEntity：关联表是"物理删除 + 先删后插"的用法，
 * 既不需要审计字段也不需要逻辑删除，带上那些列只会让表更宽、SQL 更啰嗦。
 */
@Data
@TableName("sys_user_role")
@Schema(description = "用户角色关联")
public class SysUserRole implements Serializable {

    @Schema(description = "主键")
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @Schema(description = "用户ID")
    private Long userId;

    @Schema(description = "角色ID")
    private Long roleId;
}
