package com.xujinzhou.blogbackend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;

@Configuration
public class RedisConfig {

    /**
     * 自定义 RedisTemplate：key 用字符串，value 用 JSON

     * 让 redis-cli 里能直接看懂、别的语言也能读；
     * 同时让容器里存在 RedisTemplate<String, Object> 这个 Bean
     * （Spring Boot 默认自动配置的是 RedisTemplate<Object, Object>，类型对不上）

     * 【和旧写法的两处关键差异】
     * 1. 类名去掉了 "2"：GenericJacksonJsonRedisSerializer（新）
     *    旧的是 GenericJackson2JsonRedisSerializer —— 自 Spring Data Redis 4.0 起
     *    已弃用并标记移除。
     *    背景：Spring Boot 4 把 Jackson 从 2.x 升到 3.x（包名 com.fasterxml.jackson
     *         → tools.jackson），序列化器跟着换代。

     * 2. 新类【没有无参构造】，必须用 builder() 创建；
     *    enableUnsafeDefaultTyping() 开启类型信息（写入 @class 字段），
     *    等价于旧类自动开启 default typing 的行为。
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory factory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);

        StringRedisSerializer keySerializer = new StringRedisSerializer();
        GenericJacksonJsonRedisSerializer valueSerializer = GenericJacksonJsonRedisSerializer.builder()
                .enableUnsafeDefaultTyping()
                .build();

        template.setKeySerializer(keySerializer);          // 普通 key
        template.setHashKeySerializer(keySerializer);      // Hash 结构的 key
        template.setValueSerializer(valueSerializer);      // 普通 value
        template.setHashValueSerializer(valueSerializer);  // Hash 结构的 value

        template.afterPropertiesSet();
        return template;
    }

    /**
     * 缓存管理器：让 @Cacheable / @CacheEvict 这类【缓存注解】也用 JSON 序列化

     * 为什么需要单独配它？（高频坑）
     *   Spring 有两套用 Redis 的路子，序列化由【不同对象】决定：
     *     ① 直接用 RedisTemplate       → 由 RedisTemplate 的序列化器决定
     *     ② 用 @Cacheable 等注解       → 由 RedisCacheManager 决定，和 RedisTemplate 无关！
     *   只配了 ①、忘了配 ②，用注解缓存时写进去的还是乱码。
     
     * 本项目当前是手动用 RedisTemplate 读写（没用注解），所以这个 Bean 暂时用不到；
     * 但先配上，等以后改用 @Cacheable 时就不会踩坑。
     */
    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory factory) {
        GenericJacksonJsonRedisSerializer valueSerializer = GenericJacksonJsonRedisSerializer.builder()
                .enableUnsafeDefaultTyping()
                .build();

        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(10))   // 默认 10 分钟过期
                .serializeKeysWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(valueSerializer));

        return RedisCacheManager.builder(factory)
                .cacheDefaults(config)
                .build();
    }
}