package com.lizp.tone.module.system.dept.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.lizp.tone.common.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 部门实体 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_dept")
@Schema(description = "部门")
public class SysDept extends BaseEntity {

    @Schema(description = "父级ID，顶级为0")
    private Long parentId;

    /** 形如 "0,100,101"，查所有下级时用 FIND_IN_SET，避免递归查询 */
    @Schema(description = "祖级链")
    private String ancestors;

    @Schema(description = "部门名称")
    private String deptName;

    @Schema(description = "负责人")
    private String leader;

    @Schema(description = "联系电话")
    private String phone;

    @Schema(description = "邮箱")
    private String email;

    @Schema(description = "排序")
    private Integer orderNum;

    @Schema(description = "状态：0正常 1停用")
    private Integer status;

    @Schema(description = "备注")
    private String remark;
}
