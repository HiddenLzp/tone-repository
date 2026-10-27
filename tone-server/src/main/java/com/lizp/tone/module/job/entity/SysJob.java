package com.lizp.tone.module.job.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.lizp.tone.common.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 定时任务实体 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_job")
@Schema(description = "定时任务")
public class SysJob extends BaseEntity {

    @Schema(description = "任务名称")
    private String jobName;

    @Schema(description = "任务分组")
    private String jobGroup;

    @Schema(description = "调用目标，格式 bean.method(参数)")
    private String invokeTarget;

    @Schema(description = "cron 表达式")
    private String cron;

    @Schema(description = "错过执行策略：1立即执行 2执行一次 3放弃")
    private Integer misfirePolicy;

    @Schema(description = "是否并发：0允许 1禁止")
    private Integer concurrent;

    @Schema(description = "状态：0正常 1暂停")
    private Integer status;

    @Schema(description = "备注")
    private String remark;
}
