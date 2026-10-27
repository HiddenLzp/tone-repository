package com.lizp.tone.common;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 实体公共父类：主键 + 审计字段 + 逻辑删除。
 *
 * <p>业务实体继承它即可自动获得审计字段自动填充（见 AuditMetaObjectHandler）。
 */
@Data
public class BaseEntity implements Serializable {

    @Schema(description = "主键")
    @TableId(value = "id")
    private Long id;

    @Schema(description = "创建者")
    @TableField(fill = FieldFill.INSERT)
    private Long createBy;

    @Schema(description = "创建时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @Schema(description = "更新者")
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long updateBy;

    @Schema(description = "更新时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /**
     * 逻辑删除标记：0存在 2删除。
     * 加了 @TableLogic 后，MP 的 deleteById 会自动转成 UPDATE ... SET del_flag=2，
     * 查询也会自动带上 del_flag=0 条件，业务代码无需关心。
     */
    @Schema(description = "删除标记：0存在 2删除", hidden = true)
    @TableLogic
    private Integer delFlag;
}
