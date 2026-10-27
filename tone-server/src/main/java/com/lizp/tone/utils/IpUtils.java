package com.lizp.tone.utils;

import cn.hutool.core.util.StrUtil;
import jakarta.servlet.http.HttpServletRequest;

/**
 * IP 工具类。
 *
 * <p>经过 Nginx / 网关时，{@code request.getRemoteAddr()} 拿到的是代理服务器的 IP，
 * 所以必须按顺序从代理头里取真实客户端 IP。这些头可以被伪造，
 * 因此仅用于展示与限流，不能作为安全判定的唯一依据。
 */
public final class IpUtils {

    private static final String UNKNOWN = "unknown";

    private static final String[] IP_HEADERS = {
            "X-Forwarded-For",
            "Proxy-Client-IP",
            "WL-Proxy-Client-IP",
            "HTTP_CLIENT_IP",
            "HTTP_X_FORWARDED_FOR",
            "X-Real-IP"
    };

    private IpUtils() {
    }

    public static String getIp(HttpServletRequest request) {
        if (request == null) {
            return "127.0.0.1";
        }
        for (String header : IP_HEADERS) {
            String ip = request.getHeader(header);
            if (isUnknown(ip)) {
                continue;
            }
            // X-Forwarded-For 可能是 "客户端IP, 代理1, 代理2"，第一个才是真实客户端
            int commaIndex = ip.indexOf(',');
            if (commaIndex > 0) {
                ip = ip.substring(0, commaIndex).trim();
            }
            if (isValidIp(ip)) {
                return ip;
            }
        }
        String remoteAddr = request.getRemoteAddr();
        // IPv6 的本地回环统一转成 IPv4 形式，避免入库展示不一致
        if ("0:0:0:0:0:0:0:1".equals(remoteAddr) || "::1".equals(remoteAddr)) {
            return "127.0.0.1";
        }
        return remoteAddr;
    }

    /** 是否为内网 IP（含本机、A/B/C 类私有网段、回环） */
    public static boolean isInternalIp(String ip) {
        if (StrUtil.isBlank(ip) || UNKNOWN.equalsIgnoreCase(ip)) {
            return true;
        }
        if ("127.0.0.1".equals(ip) || "::1".equals(ip) || "localhost".equalsIgnoreCase(ip)) {
            return true;
        }
        String[] parts = ip.split("\\.");
        if (parts.length != 4) {
            // 非 IPv4（IPv6 或局域网主机名）一律按内网处理
            return true;
        }
        int a = Integer.parseInt(parts[0]);
        int b = Integer.parseInt(parts[1]);
        return a == 10
                || (a == 172 && b >= 16 && b <= 31)
                || (a == 192 && b == 168)
                || (a == 169 && b == 254);
    }

    private static boolean isUnknown(String ip) {
        return StrUtil.isBlank(ip) || UNKNOWN.equalsIgnoreCase(ip);
    }

    private static boolean isValidIp(String ip) {
        return !isUnknown(ip) && ip.matches("^[0-9a-fA-F:.]+$");
    }
}
