package com.lizp.tone.module.system.user.dto;

import com.lizp.tone.common.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/** 用户查询条件 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "用户查询条件")
public class UserQuery extends PageQuery {

    @Schema(description = "账号或昵称")
    private String keyword;

    @Schema(description = "手机号")
    private String phone;

    @Schema(description = "状态：0正常 1停用")
    private Integer status;

    @Schema(description = "部门ID")
    private Long deptId;

    @Schema(description = "创建时间起")
    private LocalDateTime beginTime;

    @Schema(description = "创建时间止")
    private LocalDateTime endTime;
}
