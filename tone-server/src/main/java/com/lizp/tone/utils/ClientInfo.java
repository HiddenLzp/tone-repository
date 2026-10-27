package com.lizp.tone.utils;

import cn.hutool.http.useragent.UserAgent;
import cn.hutool.http.useragent.UserAgentUtil;
import jakarta.servlet.http.HttpServletRequest;

/**
 * 客户端信息快照（IP / 归属地 / 浏览器 / 操作系统）。
 *
 * <p>存在的唯一理由：异步写日志时不能在子线程里再碰 {@code HttpServletRequest}。
 * Tomcat 会复用请求对象，controller 返回后该对象就被回收了，子线程再去读会抛
 * {@code IllegalStateException: The request object has been recycled}。
 * 因此必须在请求线程内先把需要的值「拍」下来，再交给异步任务。
 */
public record ClientInfo(String ip, String location, String browser, String os) {

    private static final String UNKNOWN = "未知";

    /** 在请求线程内同步采集一次快照。 */
    public static ClientInfo capture(HttpServletRequest request) {
        if (request == null) {
            return empty();
        }
        String ip = IpUtils.getIp(request);
        String location = AddressUtils.getAddress(ip);
        String uaHeader = request.getHeader("User-Agent");
        UserAgent ua = UserAgentUtil.parse(uaHeader);
        String browser = ua == null || ua.getBrowser() == null ? UNKNOWN : ua.getBrowser().getName();
        String os = ua == null || ua.getOs() == null ? UNKNOWN : ua.getOs().getName();
        return new ClientInfo(ip, location, browser, os);
    }

    public static ClientInfo empty() {
        return new ClientInfo(UNKNOWN, UNKNOWN, UNKNOWN, UNKNOWN);
    }
}
