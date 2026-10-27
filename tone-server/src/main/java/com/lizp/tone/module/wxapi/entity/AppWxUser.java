package com.lizp.tone.module.wxapi.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.lizp.tone.common.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 小程序用户（首次登录时创建）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("app_wx_user")
@Schema(description = "小程序用户")
public class AppWxUser extends BaseEntity {

    @Schema(description = "微信 openId（唯一）")
    private String openId;

    @Schema(description = "昵称")
    private String nickname;

    @Schema(description = "头像地址")
    private String avatar;

    @Schema(description = "最近活跃时间")
    private java.time.LocalDateTime lastActiveAt;

    @Schema(description = "最近访问 IP")
    private String lastIp;

    @Schema(description = "最近访问归属地（ip2region 离线解析）")
    private String lastLocation;

    @Schema(description = "最近访问 User-Agent（设备/微信版本）")
    private String lastUa;

    @Schema(description = "访问次数（按 5 分钟节流计的活跃次数）")
    private Long visitCount;
}
