package com.xujinzhou.blogbackend.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 配置
 *
 * 【为什么必须注册分页插件？】
 *   如果不注册，当你用 Page<T> 查询时，MyBatis-Plus 不会执行 LIMIT，
 *   而是【把所有数据查出来再在内存里截取】——
 *   数据量一大就直接把内存撑爆，而且日志里看不到 LIMIT 语句。
 *
 *   这是新手最容易踩的坑之一：
 *      代码看起来没问题、返回结果也对，
 *      但数据库其实被全表扫描了，只是数据量小的时候发现不了。
 *
 * 【为什么在 config 里单独建一个类，而不是塞进 RedisConfig？】
 *   职责分离：RedisConfig 管缓存序列化，MybatisPlusConfig 管 ORM 插件。
 *   混在一起以后找起来麻烦。
 */
@Configuration
public class MybatisPlusConfig {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();

        // 分页插件：指定数据库类型为 MySQL（它会据此生成 LIMIT 语句）
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));

        return interceptor;
    }
}
