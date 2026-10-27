package com.lizp.tone.module.monitor.dto;

import com.lizp.tone.common.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/** 登录日志查询条件 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "登录日志查询条件")
public class LoginLogQuery extends PageQuery {

    @Schema(description = "登录账号")
    private String username;

    @Schema(description = "登录IP")
    private String ip;

    @Schema(description = "状态：0成功 1失败")
    private Integer status;

    @Schema(description = "开始时间")
    private LocalDateTime beginTime;

    @Schema(description = "结束时间")
    private LocalDateTime endTime;
}
