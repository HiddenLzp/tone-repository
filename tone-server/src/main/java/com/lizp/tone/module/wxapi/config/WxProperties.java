package com.lizp.tone.module.wxapi.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 微信小程序配置，对应 application.yml 的 {@code tone.wx.*}。
 */
@Data
@Component
@ConfigurationProperties(prefix = "tone.wx")
public class WxProperties {

    /** 小程序 appid；为空时走 dev 模式：按 code 生成稳定的 mock openId */
    private String appid;

    private String secret;

    /**
     * 模拟登录开关：true 时忽略 appid/secret，按客户端 deviceId 生成稳定的 mock openId。
     *
     * <p>为什么不能用「appid 是否为空」来推断（评审指出的坑）：appid 的有无只说明
     * "有没有微信凭据"，不说明"要不要模拟登录"——开发期同样可能配了 appid
     * （想在开发者工具里测真实 code2session），按有无推断会让 mock 登录静默失效、
     * 每个登录都去打真实微信接口，开发机没外网或凭据没配全就直接登录失败。
     * 所以拆成显式开关：dev 配置里默认 true，prod 不配置（默认 false）永远走真实登录。
     */
    private boolean mockLogin = false;

    /** 小程序端独立 JWT 签名密钥（与管理台 jwt-secret 分开） */
    private String tokenSecret = "toneWxDefaultTokenSecretPleaseChangeInProduction1234567890";

    /** 小程序 token 有效期（天） */
    private int tokenExpireDays = 30;

    /** code2session 接口地址 */
    private String code2sessionUrl = "https://api.weixin.qq.com/sns/jscode2session";
}
