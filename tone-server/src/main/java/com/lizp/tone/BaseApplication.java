package com.lizp.tone;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 启动类。
 *
 * <p>@MapperScan 统一扫描，省得每个 Mapper 接口都加 @Mapper；
 * 上面的 Mapper 接口仍然保留 @Mapper 注解，是为了 IDE 单独使用时也能识别。
 */
@EnableScheduling
@SpringBootApplication
@MapperScan("com.lizp.tone.**.mapper")
public class BaseApplication {

    public static void main(String[] args) {
        SpringApplication.run(BaseApplication.class, args);
    }
}
