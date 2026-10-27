package com.lizp.tone.module.monitor.dto;

import com.lizp.tone.common.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/** 操作日志查询条件 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "操作日志查询条件")
public class OperationLogQuery extends PageQuery {

    @Schema(description = "模块标题")
    private String title;

    @Schema(description = "操作人员")
    private String operName;

    @Schema(description = "业务类型：0其它 1新增 2修改 3删除 4导出 5导入 6授权 7强退")
    private Integer businessType;

    @Schema(description = "状态：0成功 1失败")
    private Integer status;

    @Schema(description = "开始时间")
    private LocalDateTime beginTime;

    @Schema(description = "结束时间")
    private LocalDateTime endTime;
}
