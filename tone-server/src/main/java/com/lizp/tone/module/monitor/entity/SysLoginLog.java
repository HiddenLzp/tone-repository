package com.lizp.tone.module.monitor.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/** 登录日志实体 */
@Data
@TableName("sys_login_log")
@Schema(description = "登录日志")
public class SysLoginLog implements Serializable {

    @Schema(description = "主键")
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @Schema(description = "登录账号")
    private String username;

    @Schema(description = "登录IP")
    private String ip;

    @Schema(description = "IP归属地")
    private String location;

    @Schema(description = "浏览器")
    private String browser;

    @Schema(description = "操作系统")
    private String os;

    @Schema(description = "状态：0成功 1失败")
    private Integer status;

    @Schema(description = "提示消息（失败原因）")
    private String msg;

    @Schema(description = "登录时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime loginTime;
}
