package com.lizp.tone.module.agent.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.lizp.tone.common.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 语气检查记录。
 *
 * <p>scores/issues/versions 原样存 JSON 字符串，由 VO 层反序列化后返回前端，
 * 结构演进不需要改表。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("app_check_record")
@Schema(description = "语气检查记录")
public class AppCheckRecord extends BaseEntity {

    @Schema(description = "小程序用户 openId")
    private String openId;

    @Schema(description = "场景：workplace/social/relationship/moments/service")
    private String scene;

    @Schema(description = "原文")
    private String originalText;

    @Schema(description = "评分 JSON：politeness/professionalism/emotion")
    private String scores;

    @Schema(description = "问题列表 JSON")
    private String issues;

    @Schema(description = "改写版本 JSON：[{style,text}]")
    private String versions;

    @Schema(description = "用户选定的版本序号（0/1/2），未选为 -1")
    private Integer selectedVersion;

    @Schema(description = "最终采用文本（含微调后的最新版）")
    private String finalText;

    @Schema(description = "Agent 实际执行轮数")
    private Integer rounds;

    @Schema(description = "本次请求累计 prompt tokens")
    private Long promptTokens;

    @Schema(description = "本次请求累计 completion tokens")
    private Long completionTokens;

    /** 敏感词拦截等不落库状态通过 status 区分 */
    @Schema(description = "状态：done完成 blocked敏感词拦截 error异常")
    private String status;
}
