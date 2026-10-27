package com.lizp.tone.module.wxapi.controller;

import com.lizp.tone.aspect.RateLimiter;
import com.lizp.tone.common.R;
import com.lizp.tone.module.wxapi.service.WxAuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 小程序登录：wx.login 的 code 换 token。
 */
@Validated
@RestController
@RequestMapping("/api/wx")
@RequiredArgsConstructor
public class WxAuthController {

    private final WxAuthService wxAuthService;

    @Data
    @Schema(description = "小程序登录请求")
    public static class WxLoginRequest {
        @NotBlank(message = "缺少微信 code")
        @Schema(description = "wx.login 返回的 code")
        private String code;

        @Schema(description = "设备标识（dev 模式用于生成稳定 openId）")
        private String deviceId;
    }

    @PostMapping("/login")
    @RateLimiter(time = 60, count = 20)
    @Operation(summary = "小程序登录")
    public R<WxAuthService.WxLogin> login(@jakarta.validation.Valid @RequestBody WxLoginRequest request) {
        return R.ok(wxAuthService.login(request.getCode(), request.getDeviceId()));
    }
}
