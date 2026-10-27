package com.lizp.tone.module.wxapi.controller;

import com.lizp.tone.common.R;
import com.lizp.tone.module.wxapi.auth.WxUserContext;
import com.lizp.tone.module.wxapi.service.QuotaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 配额接口：查询剩余次数 / 看完广告领取奖励。
 */
@Tag(name = "小程序配额")
@RestController
@RequestMapping("/api/quota")
@RequiredArgsConstructor
public class QuotaController {

    private final QuotaService quotaService;

    @GetMapping("/check")
    @Operation(summary = "查询今日配额")
    public R<QuotaService.QuotaSnapshot> check() {
        return R.ok(quotaService.snapshot(WxUserContext.getOpenId()));
    }

    @PostMapping("/reward")
    @Operation(summary = "看完激励视频后领取 1 次奖励")
    public R<QuotaService.QuotaSnapshot> reward() {
        return R.ok(quotaService.claimReward(WxUserContext.getOpenId(), null));
    }
}
