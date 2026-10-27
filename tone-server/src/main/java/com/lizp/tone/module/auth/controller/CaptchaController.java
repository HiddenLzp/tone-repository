package com.lizp.tone.module.auth.controller;

import cn.hutool.captcha.CaptchaUtil;
import cn.hutool.captcha.CircleCaptcha;
import cn.hutool.core.util.IdUtil;
import com.lizp.tone.common.Constants;
import com.lizp.tone.common.R;
import com.lizp.tone.config.BaseProperties;
import com.lizp.tone.utils.RedisUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.TimeUnit;

/** 图形验证码 */
@Tag(name = "验证码")
@RestController
@RequestMapping("/captcha")
@RequiredArgsConstructor
public class CaptchaController {

    private static final long CAPTCHA_TTL_SECONDS = 120;

    private final RedisUtils redisUtils;
    private final BaseProperties properties;

    @Operation(summary = "获取图形验证码", description = "返回 base64 图片与验证码ID，登录时一并提交")
    @GetMapping("/image")
    public R<CaptchaVO> image() {
        CircleCaptcha captcha = CaptchaUtil.createCircleCaptcha(120, 40, 4, 20);
        String key = IdUtil.fastSimpleUUID();
        // 验证码正确结果存 Redis（服务端不把答案下发给前端），120 秒后自动失效
        redisUtils.set(Constants.KEY_CAPTCHA + key, captcha.getCode(), CAPTCHA_TTL_SECONDS, TimeUnit.SECONDS);
        return R.ok(new CaptchaVO(properties.getSecurity().isCaptchaEnabled(), key, captcha.getImageBase64()));
    }

    /** @param enabled 是否开启验证码，前端据此决定是否展示输入框 */
    public record CaptchaVO(Boolean enabled, String captchaKey, String imageBase64) {
    }
}
