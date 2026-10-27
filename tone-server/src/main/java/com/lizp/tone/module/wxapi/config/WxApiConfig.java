package com.lizp.tone.module.wxapi.config;

import com.lizp.tone.module.wxapi.auth.WxAuthInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 小程序接口的拦截器注册：/api/** 全部鉴权，登录与广告配置放行。
 */
@Configuration
@RequiredArgsConstructor
public class WxApiConfig implements WebMvcConfigurer {

    private final WxAuthInterceptor wxAuthInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(wxAuthInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns("/api/wx/login", "/api/ad/config");
    }
}
