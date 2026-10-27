package com.lizp.tone.module.system.dict.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.lizp.tone.common.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 字典数据实体 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_dict_data")
@Schema(description = "字典数据")
public class SysDictData extends BaseEntity {

    @Schema(description = "字典类型")
    private String dictType;

    @Schema(description = "字典标签（展示值）")
    private String dictLabel;

    @Schema(description = "字典键值（存储值）")
    private String dictValue;

    @Schema(description = "排序")
    private Integer dictSort;

    @Schema(description = "是否默认：0否 1是")
    private Integer isDefault;

    @Schema(description = "标签样式：default/primary/success/info/warning/danger")
    private String listClass;

    @Schema(description = "自定义样式类")
    private String cssClass;

    @Schema(description = "状态：0正常 1停用")
    private Integer status;

    @Schema(description = "备注")
    private String remark;
}
