package com.lizp.tone.utils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

/**
 * 文件访问签名：给出「能证明服务端曾经放行过」的短时效凭证。
 *
 * <p><b>为什么不是登录态</b>：{@code <img src="...">} 发不出 Authorization 头，
 * 把受控资源挂在登录态上，图片就永远加载不出来。签名塞在 query 里，浏览器能自己带上。
 *
 * <p><b>为什么不是随机数入库</b>：签名是无状态的，不用额外表和 Redis，
 * 到期自动失效；代价是无法逐个吊销——文件受控访问的场景里，半小时有效期已经够短。
 *
 * <p>签名对象 = {@code id + 到期时间戳}，改动其中任何一个都校验不过，
 * 所以拿到链接的人不能改 id 去看别人的文件，也不能把到期时间往后挪。
 */
public final class FileSignUtils {

    private static final String ALGORITHM = "HmacSHA256";

    private FileSignUtils() {
    }

    /**
     * 生成签名。
     *
     * @param secret 签名密钥（复用 JWT 密钥，不需要额外配置项）
     * @param id     文件 id
     * @param expire 到期时间戳（秒）
     * @return 签名串（URL 安全 Base64）
     */
    public static String sign(String secret, long id, long expire) {
        String payload = id + ":" + expire;
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ALGORITHM));
            byte[] raw = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        } catch (Exception e) {
            throw new IllegalStateException("生成文件签名失败", e);
        }
    }

    /**
     * 校验签名是否正确且未过期。
     *
     * @param secret 签名密钥
     * @param id     文件 id
     * @param expire 到期时间戳（秒）
     * @param sign   待校验的签名串
     * @return true 表示放行
     */
    public static boolean verify(String secret, long id, long expire, String sign) {
        if (sign == null || sign.isBlank() || expire <= 0) {
            return false;
        }
        // 常量时间比较，避免通过响应耗时逐字节爆破签名
        String expected = sign(secret, id, expire);
        byte[] a = expected.getBytes(StandardCharsets.UTF_8);
        byte[] b = sign.getBytes(StandardCharsets.UTF_8);
        boolean matched = MessageDigest.isEqual(a, b);
        if (!matched) {
            return false;
        }
        long now = System.currentTimeMillis() / 1000;
        return now <= expire;
    }
}
