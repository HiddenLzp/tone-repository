package com.lizp.tone.module.wxapi.service;

import cn.hutool.crypto.digest.DigestUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lizp.tone.common.BaseException;
import com.lizp.tone.common.ErrorCode;
import com.lizp.tone.module.wxapi.config.WxProperties;
import com.lizp.tone.module.wxapi.entity.AppWxUser;
import com.lizp.tone.module.wxapi.mapper.AppWxUserMapper;
import com.lizp.tone.utils.AddressUtils;
import com.lizp.tone.utils.IpUtils;
import com.lizp.tone.utils.ServletUtils;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.client.RestClient;

import java.time.LocalDateTime;

/**
 * 微信登录：wx.login 拿到的 code 换 openId。
 *
 * <p>未配置 appid/secret 时进入 dev 模式：按客户端传的 deviceId 生成稳定 mock openId，
 * 不依赖真实小程序身份即可打通全流程。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WxAuthService {

    private final WxProperties wxProperties;
    private final AppWxUserMapper appWxUserMapper;
    private final ObjectMapper objectMapper;
    private final WxTokenService wxTokenService;

    public record WxLogin(String openId, String token, boolean devMode, long expiresDays) {
    }

    public WxLogin login(String code, String deviceId) {
        String openId = resolveOpenId(code, deviceId);
        upsertUser(openId);
        String token = wxTokenService.issue(openId);
        // devMode 语义 = mockLogin 开关（而不是 appid 是否为空）：前端据此显示"重置次数"等开发辅助
        return new WxLogin(openId, token, wxProperties.isMockLogin(), wxProperties.getTokenExpireDays());
    }

    private String resolveOpenId(String code, String deviceId) {
        if (wxProperties.isMockLogin()) {
            // 模拟登录：deviceId 稳定则 openId 稳定（同一台开发机反复测试拿到的是同一个用户）
            String seed = StringUtils.hasText(deviceId) ? deviceId : code;
            return "dev_" + DigestUtil.md5Hex(seed).substring(0, 16);
        }
        // 真实登录：凭据不全时给出明确报错，而不是把空 appid 发给微信拿到一个含糊的 errcode
        if (!StringUtils.hasText(wxProperties.getAppid()) || !StringUtils.hasText(wxProperties.getSecret())) {
            throw new BaseException(ErrorCode.WX_LOGIN_FAILED,
                    "未配置微信 appid/secret，且 mock-login=false，无法登录");
        }
        try {
            String body = RestClient.create().get()
                    .uri(wxProperties.getCode2sessionUrl(), uri -> uri.queryParam("appid", wxProperties.getAppid())
                            .queryParam("secret", wxProperties.getSecret())
                            .queryParam("js_code", code)
                            .queryParam("grant_type", "authorization_code")
                            .build())
                    .retrieve()
                    .body(String.class);
            JsonNode node = objectMapper.readTree(body == null ? "{}" : body);
            if (node.path("errcode").asInt(0) != 0 || !StringUtils.hasText(node.path("openid").asText())) {
                log.warn("code2session 失败: {}", body);
                throw new BaseException(ErrorCode.WX_LOGIN_FAILED);
            }
            return node.path("openid").asText();
        } catch (BaseException e) {
            throw e;
        } catch (Exception e) {
            log.error("code2session 调用异常", e);
            throw new BaseException(ErrorCode.WX_API_ERROR);
        }
    }

    /**
     * 登录时维护用户行。登录接口在拦截器的 excludePathPatterns 里（/api/wx/login），
     * 拦截器的访问采集不会覆盖它，所以 IP/UA 要在这里单独记录一次。
     * 请求信息通过 ServletUtils 从当前线程取（login 本身跑在 HTTP 线程上，可用）。
     */
    private void upsertUser(String openId) {
        AppWxUser user = appWxUserMapper.selectOne(
                Wrappers.<AppWxUser>lambdaQuery().eq(AppWxUser::getOpenId, openId));
        boolean isNew = user == null;
        if (isNew) {
            user = new AppWxUser();
            user.setOpenId(openId);
            user.setVisitCount(1L);
        } else {
            user.setVisitCount((user.getVisitCount() == null ? 0 : user.getVisitCount()) + 1);
        }
        user.setLastActiveAt(LocalDateTime.now());
        HttpServletRequest request = ServletUtils.getRequest();
        if (request != null) {
            String ip = IpUtils.getIp(request);
            user.setLastIp(ip);
            user.setLastLocation(AddressUtils.getAddress(ip));
            user.setLastUa(StrUtil.maxLength(request.getHeader("User-Agent"), 250));
        }
        if (isNew) {
            appWxUserMapper.insert(user);
        } else {
            appWxUserMapper.updateById(user);
        }
    }
}
