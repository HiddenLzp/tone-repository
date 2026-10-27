package com.lizp.tone.module.system.role.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/** 角色与资源（菜单/按钮）关联实体。同 {@link SysUserRole}，不继承 BaseEntity。 */
@Data
@TableName("sys_role_menu")
@Schema(description = "角色资源关联")
public class SysRoleMenu implements Serializable {

    @Schema(description = "主键")
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @Schema(description = "角色ID")
    private Long roleId;

    @Schema(description = "资源ID")
    private Long menuId;
}
