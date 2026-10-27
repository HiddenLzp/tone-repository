package com.lizp.tone.module.wxapi.controller;

import com.lizp.tone.common.R;
import com.lizp.tone.module.wxapi.auth.WxUserContext;
import com.lizp.tone.module.wxapi.service.QuotaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 开发辅助接口：一键重置当前用户今日配额（免费 + 奖励）。
 *
 * <p>安全护栏：整个 Controller 用 {@code @ConditionalOnProperty(tone.dev-tools-enabled)}
 * 装配——该开关只在 application-dev.yml 里为 true，prod/test 配置里根本没有这个键，
 * 默认值 false 意味着生产环境连这个 Bean 都不存在、接口 404，不存在被调用的可能。
 * （不用 @Profile("dev") 的原因：spring.profiles.active 默认就是 dev，忘了改的话
 * profile 护栏会形同虚设；显式开关的默认关闭语义更安全。）
 *
 * <p>鉴权照常走 WxAuthInterceptor：必须带小程序 token，重置的是"当前 token 用户"
 * 的配额，不提供按 openId 重置他人额度的能力。
 */
@Tag(name = "开发辅助（生产不装配）")
@RestController
@RequestMapping("/api/quota")
@ConditionalOnProperty(name = "tone.dev-tools-enabled", havingValue = "true")
@RequiredArgsConstructor
public class DevQuotaController {

    private final QuotaService quotaService;

    @PostMapping("/reset")
    @Operation(summary = "[开发]重置当前用户今日配额，返回重置后的快照")
    public R<QuotaService.QuotaSnapshot> reset() {
        return R.ok(quotaService.reset(WxUserContext.getOpenId()));
    }
}
