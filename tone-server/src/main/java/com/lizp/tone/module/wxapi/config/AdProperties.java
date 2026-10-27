package com.lizp.tone.module.wxapi.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 激励视频广告配置，对应 application.yml 的 {@code tone.ad.*}。
 */
@Data
@Component
@ConfigurationProperties(prefix = "tone.ad")
public class AdProperties {

    /** 微信激励视频广告位 ID（流量主后台申请）；为空时小程序隐藏广告入口 */
    private String unitId;
}
