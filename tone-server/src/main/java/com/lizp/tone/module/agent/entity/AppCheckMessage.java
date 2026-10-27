package com.lizp.tone.module.agent.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.lizp.tone.common.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 检查记录的消息明细：原始 Agent 对话与微调对话都追加在这里，refine 时重建上下文。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("app_check_message")
@Schema(description = "检查记录消息明细")
public class AppCheckMessage extends BaseEntity {

    @Schema(description = "检查记录 id")
    private Long recordId;

    @Schema(description = "角色：user / assistant / tool")
    private String role;

    @Schema(description = "内容")
    private String content;

    @Schema(description = "是否属于微调对话（区别于首轮检查）")
    private Integer refineFlag;
}
