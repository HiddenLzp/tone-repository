package com.lizp.tone.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI 3 接口文档配置（springdoc）。
 *
 * <p>访问地址：{@code http://localhost:8080/swagger-ui.html}
 *
 * <p>配了 Bearer 认证后，swagger 页面上的「Authorize」按钮填入 accessToken，
 * 调试接口时会自动带上 Authorization 头，不用手动加。
 */
@Configuration
public class OpenApiConfig {

    private static final String SECURITY_SCHEME = "bearerAuth";

    @Bean
    public OpenAPI baseOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("base-framework 后台管理接口文档")
                        .description("通用后台管理脚手架 —— 系统管理 / 权限 / 日志 / 定时任务")
                        .version("v1.0.0")
                        .license(new License().name("Apache 2.0")))
                .components(new Components().addSecuritySchemes(SECURITY_SCHEME,
                        new SecurityScheme()
                                .name(SECURITY_SCHEME)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("填入登录接口返回的 accessToken")))
                // 全局生效，避免每个接口都写 @SecurityRequirement
                .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME));
    }
}
