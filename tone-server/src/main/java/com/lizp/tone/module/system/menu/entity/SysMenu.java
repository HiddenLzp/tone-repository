package com.lizp.tone.module.system.menu.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.lizp.tone.common.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 菜单/资源实体：目录、菜单、按钮(接口) 三类共用一张表 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_menu")
@Schema(description = "菜单资源")
public class SysMenu extends BaseEntity {

    @Schema(description = "父级ID，顶级为0")
    private Long parentId;

    @Schema(description = "资源名称")
    private String menuName;

    @Schema(description = "类型：1目录 2菜单 3按钮/接口")
    private Integer menuType;

    @Schema(description = "路由地址(菜单) 或 接口路径(按钮)")
    private String path;

    @Schema(description = "前端组件路径")
    private String component;

    @Schema(description = "权限标识")
    private String perms;

    @Schema(description = "图标")
    private String icon;

    @Schema(description = "排序")
    private Integer orderNum;

    @Schema(description = "是否显示：0显示 1隐藏")
    private Integer visible;

    @Schema(description = "状态：0正常 1停用")
    private Integer status;

    @Schema(description = "是否缓存：0否 1是")
    private Integer isCache;

    @Schema(description = "是否外链：0否 1是")
    private Integer isExternal;

    @Schema(description = "备注")
    private String remark;
}
