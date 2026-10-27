package com.lizp.tone.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;

/**
 * Web MVC 配置：把本地上传目录映射成可直接访问的静态资源。
 *
 * <p>例如 {@code base.file.local-path=./upload} 时，
 * {@code /upload/2026/10/xx.jpg} 会直接命中磁盘文件。
 * 生产环境建议改由 Nginx 直接托管静态文件，不走应用进程。
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final BaseProperties properties;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String urlPrefix = properties.getFile().getUrlPrefix();
        Path absolute = Path.of(properties.getFile().getLocalPath()).toAbsolutePath().normalize();
        registry.addResourceHandler(urlPrefix + "/**")
                .addResourceLocations("file:" + absolute + "/");
        log.info("静态资源映射：{}/** -> {}", urlPrefix, absolute);
    }
}
