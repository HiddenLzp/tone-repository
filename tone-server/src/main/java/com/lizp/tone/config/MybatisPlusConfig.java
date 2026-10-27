package com.lizp.tone.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.DataPermissionInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.lizp.tone.dataperm.DataScopeHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 配置。
 *
 * <p><b>拦截器顺序很关键</b>：DataPermissionInterceptor 必须排在 PaginationInnerInterceptor 前面。
 * 反过来的话，分页插件先拿到原始 SQL 算了 count，数据权限条件还没拼上，
 * 会出现「总数对、列表数据少」或者「列表对、总数虚高」这种很隐蔽的 bug。
 */
@Configuration
public class MybatisPlusConfig {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        // 1. 数据权限：拼行级过滤条件
        interceptor.addInnerInterceptor(new DataPermissionInterceptor(new DataScopeHandler()));
        // 2. 分页：必须放在数据权限之后
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        // 3. 乐观锁：实体上加 @Version 字段即可生效
        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
        return interceptor;
    }
}
