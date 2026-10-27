package com.lizp.tone.module.wxapi.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.lizp.tone.common.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/**
 * 广告奖励流水：每次看完激励视频加一条。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("app_ad_reward")
@Schema(description = "广告奖励流水")
public class AppAdReward extends BaseEntity {

    @Schema(description = "小程序用户 openId")
    private String openId;

    @Schema(description = "奖励归属日期")
    private LocalDate rewardDate;

    @Schema(description = "广告位 ID（dev 模式为 dev）")
    private String unitId;
}
