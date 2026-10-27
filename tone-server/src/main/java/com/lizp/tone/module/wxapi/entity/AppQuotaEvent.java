package com.lizp.tone.module.wxapi.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.lizp.tone.common.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/**
 * 配额事件流水：与 Redis 计数解耦的统计事实源。
 *
 * <p>Redis 计数只负责"今天还剩几次"的实时判定且 48h 过期；所有需要统计的事实
 * （免费消耗 / 奖励消耗 / 用尽被拒 / 广告领取）在这里各留一条流水，看板的
 * 配额漏斗单一来源走本表。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("app_quota_event")
@Schema(description = "配额事件流水")
public class AppQuotaEvent extends BaseEntity {

    @Schema(description = "小程序用户 openId")
    private String openId;

    @Schema(description = "事件归属日期")
    private LocalDate eventDate;

    @Schema(description = "事件：consume/consume_reward/reject/claim")
    private String eventType;

    @Schema(description = "备注（消耗的桶/广告位等）")
    private String detail;
}
