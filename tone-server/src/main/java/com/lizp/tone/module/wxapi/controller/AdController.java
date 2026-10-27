package com.lizp.tone.module.wxapi.controller;

import com.lizp.tone.common.R;
import com.lizp.tone.module.wxapi.config.AdProperties;
import com.lizp.tone.module.wxapi.config.WxProperties;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 广告配置：广告位 ID 只在后端配置，前端启动时拉取。
 *
 * <p>顺带下发 devMode（= mock-login 开关）：小程序首页的"重置次数"按钮靠它决定显示与否。
 * 之所以放这里而不是登录接口——本接口在白名单里无需登录、首页每次 onShow 都会拉取，
 * 能绕开"旧 token 一直有效导致登录流程不重跑、devMode 标志永远写不进本地存储"的问题
 * （评审实测踩到过：老版本登录的用户升级后看不到重置按钮）。
 */
@Tag(name = "小程序广告")
@RestController
@RequestMapping("/api/ad")
@RequiredArgsConstructor
public class AdController {

    private final AdProperties adProperties;
    private final WxProperties wxProperties;

    @Data
    public static class AdConfig {
        private String unitId;
        private boolean enabled;
        /** 后端是否处于模拟登录模式：前端据此显示"重置次数"等开发辅助入口 */
        private boolean devMode;
    }

    @GetMapping("/config")
    @Operation(summary = "拉取广告位配置与开发模式标志")
    public R<AdConfig> config() {
        AdConfig config = new AdConfig();
        config.setUnitId(adProperties.getUnitId());
        config.setEnabled(adProperties.getUnitId() != null && !adProperties.getUnitId().isBlank());
        config.setDevMode(wxProperties.isMockLogin());
        return R.ok(config);
    }
}
