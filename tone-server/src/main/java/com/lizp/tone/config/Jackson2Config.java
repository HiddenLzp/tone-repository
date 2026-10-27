package com.lizp.tone.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.text.SimpleDateFormat;
import java.util.TimeZone;

/**
 * Boot 4 起 Web 层默认切到 Jackson 3（tools.jackson），Jackson 2 不再自动装配 Bean。
 * 但安全基建（JWT 过滤器 / 401 403 响应体 / 操作日志脱敏）仍依赖 Jackson 2 的
 * {@link ObjectMapper}，这里显式注册一个，配置项与原 Boot 自动装配对齐。
 */
@Configuration
public class Jackson2Config {

    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
        mapper.setDateFormat(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss"));
        mapper.setTimeZone(TimeZone.getTimeZone("GMT+8"));
        return mapper;
    }
}
