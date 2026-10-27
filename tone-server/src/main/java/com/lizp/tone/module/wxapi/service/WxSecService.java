package com.lizp.tone.module.wxapi.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lizp.tone.module.wxapi.config.WxProperties;
import com.lizp.tone.utils.RedisUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * 微信内容安全检测（msgSecCheck 2.0）。
 *
 * <p>为什么必须做：小程序里用户提交的原文和 AI 生成的改写结果都属于 UGC，
 * 微信平台硬性要求接入内容安全接口，否则审核被拒、严重时封禁小程序——
 * 本地敏感词库只是自保，不能替代平台侧检测。
 *
 * <p>几个关键设计：
 * <ol>
 *   <li>access_token 缓存在 Redis（有效期 7200s，提前 5 分钟刷新，微信侧有每日
 *       获取次数上限，绝不能每请求都取）；</li>
 *   <li>msgSecCheck 2.0 要求传用户 openid（个人主体可用），所以 mock 登录模式
 *       （openid 是 dev_ 开头的假值）直接跳过检测——发了也只会报错；</li>
 *   <li>fail-open 策略：微信接口故障（网络/ token 异常等）时放行并打 WARN，
 *       安全接口挂了不能把整个业务打挂；本地敏感词库 + 平台巡检兜底。
 *       如需更严格可把下面的 FAIL_OPEN 改为 false（故障时拦截）。</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WxSecService {

    /** token 缓存键；微信 access_token 有效期 7200s，提前 5 分钟过期避免边界竞态 */
    private static final String TOKEN_KEY = "wx:access_token";
    private static final String TOKEN_URL = "https://api.weixin.qq.com/cgi-bin/token";
    private static final String SEC_CHECK_URL = "https://api.weixin.qq.com/wxa/msg_sec_check";
    /** 场景值：2=评论（用户提交内容交由 AI 处理，归属评论类场景） */
    private static final int SCENE_COMMENT = 2;
    /** 微信接口故障时是否放行（true=放行并告警，false=拦截） */
    private static final boolean FAIL_OPEN = true;

    private final WxProperties wxProperties;
    private final RedisUtils redisUtils;
    private final ObjectMapper objectMapper;

    /**
     * 检测文本是否可安全展示/处理。
     *
     * @return true=通过（或因 mock/故障被跳过），false=不通过（建议拦截）
     */
    public boolean checkText(String openId, String content) {
        // mock 登录模式：openid 是假的，msgSecCheck 2.0 必须传真实 openid，跳过检测
        if (wxProperties.isMockLogin() || !StringUtils.hasText(wxProperties.getAppid())
                || !StringUtils.hasText(wxProperties.getSecret())) {
            log.debug("内容安全检测跳过（模拟登录或凭据未配置）");
            return true;
        }
        if (!StringUtils.hasText(content)) {
            return true;
        }
        String token = getAccessToken();
        if (token == null) {
            return failOpen("获取 access_token 失败");
        }
        try {
            String body = RestClient.create().post()
                    .uri(SEC_CHECK_URL + "?access_token=" + token)
                    .header("Content-Type", "application/json")
                    .body(objectMapper.writeValueAsString(java.util.Map.of(
                            "version", 2,
                            "scene", SCENE_COMMENT,
                            "openid", openId,
                            "content", content)))
                    .retrieve()
                    .body(String.class);
            JsonNode node = objectMapper.readTree(body == null ? "{}" : body);
            int errcode = node.path("errcode").asInt(0);
            // 40001/42001 = token 失效：清缓存重取一次再试（并发刷新竞态的兜底）
            if (errcode == 40001 || errcode == 42001) {
                redisUtils.delete(TOKEN_KEY);
                return checkText(openId, content);
            }
            if (errcode != 0) {
                // 内容安全接口自身故障（频率限制/服务异常）：按 fail-open 策略处理
                return failOpen("msgSecCheck 异常 errcode=" + errcode + " body=" + body);
            }
            String suggest = node.path("result").path("suggest").asText("pass");
            if (!"pass".equalsIgnoreCase(suggest)) {
                log.warn("内容安全检测不通过 suggest={} label={} openId={}",
                        suggest, node.path("result").path("label").asInt(), openId);
                return false;
            }
            return true;
        } catch (Exception e) {
            return failOpen("msgSecCheck 调用异常: " + e.getMessage());
        }
    }

    /** access_token（Redis 缓存优先）；获取失败返回 null */
    private String getAccessToken() {
        String cached = redisUtils.getStr(TOKEN_KEY);
        if (StringUtils.hasText(cached)) {
            return cached;
        }
        try {
            String body = RestClient.create().get()
                    .uri(TOKEN_URL, uri -> uri.queryParam("grant_type", "client_credential")
                            .queryParam("appid", wxProperties.getAppid())
                            .queryParam("secret", wxProperties.getSecret())
                            .build())
                    .retrieve()
                    .body(String.class);
            JsonNode node = objectMapper.readTree(body == null ? "{}" : body);
            String token = node.path("access_token").asText("");
            if (!StringUtils.hasText(token)) {
                log.warn("获取 access_token 失败: {}", body);
                return null;
            }
            long expiresIn = node.path("expires_in").asLong(7200);
            redisUtils.set(TOKEN_KEY, token, Duration.ofSeconds(Math.max(60, expiresIn - 300)));
            return token;
        } catch (Exception e) {
            log.warn("获取 access_token 异常: {}", e.getMessage());
            return null;
        }
    }

    /** fail-open/fail-close 的统一出口，保证告警日志只有一份 */
    private boolean failOpen(String reason) {
        log.warn("内容安全检测跳过（{}），按 {} 处理", reason, FAIL_OPEN ? "放行" : "拦截");
        return FAIL_OPEN;
    }
}
