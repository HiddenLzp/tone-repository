package com.lizp.tone.module.monitor.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 操作日志实体。
 *
 * <p>不继承 BaseEntity：这张表没有逻辑删除和更新字段，写入量又大，
 * 字段越少越好；另外它只有 create_time，定时清理任务按这个字段扫描。
 */
@Data
@TableName("sys_operation_log")
@Schema(description = "操作日志")
public class SysOperationLog implements Serializable {

    @Schema(description = "主键")
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @Schema(description = "模块标题")
    private String title;

    @Schema(description = "业务类型：0其它 1新增 2修改 3删除 4导出 5导入 6授权 7强退")
    private Integer businessType;

    @Schema(description = "方法全名")
    private String method;

    @Schema(description = "HTTP方法")
    private String requestMethod;

    @Schema(description = "操作人类别：0其它 1后台用户 2手机端用户")
    private Integer operatorType;

    @Schema(description = "操作人员")
    private String operName;

    @Schema(description = "请求URL")
    private String operUrl;

    @Schema(description = "请求IP")
    private String operIp;

    @Schema(description = "IP归属地")
    private String operLocation;

    @Schema(description = "请求参数")
    private String operParam;

    @Schema(description = "返回结果")
    private String jsonResult;

    @Schema(description = "状态：0成功 1失败")
    private Integer status;

    @Schema(description = "错误消息")
    private String errorMsg;

    @Schema(description = "耗时(毫秒)")
    private Long costTime;

    @Schema(description = "操作时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
