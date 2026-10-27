package com.lizp.tone.module.wxapi.auth;

/**
 * 当前请求的微信用户上下文（openId），由 WxAuthInterceptor 写入、清理。
 */
public final class WxUserContext {

    private static final ThreadLocal<String> OPEN_ID = new ThreadLocal<>();

    private WxUserContext() {
    }

    public static void setOpenId(String openId) {
        OPEN_ID.set(openId);
    }

    /** 业务代码取当前登录的 openId；未经过拦截器的请求返回 null */
    public static String getOpenId() {
        return OPEN_ID.get();
    }

    public static void clear() {
        OPEN_ID.remove();
    }
}
