package com.lizp.tone.module.system.role.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/** 角色与部门关联实体（数据权限：自定义范围时使用）。同 {@link SysUserRole}，不继承 BaseEntity。 */
@Data
@TableName("sys_role_dept")
@Schema(description = "角色部门关联")
public class SysRoleDept implements Serializable {

    @Schema(description = "主键")
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @Schema(description = "角色ID")
    private Long roleId;

    @Schema(description = "部门ID")
    private Long deptId;
}
