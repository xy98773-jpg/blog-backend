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
     * 这样 redis-cli 里能直接看懂，别的语言写的服务也能读；
     * 同时也让容器里存在 RedisTemplate<String, Object> 这个 Bean（默认自动配置是 <Object, Object>）
     *
     * 注意两处和旧教程不一样的地方：
     * 1. 用 GenericJacksonJsonRedisSerializer，而不是 GenericJackson2JsonRedisSerializer
     *    —— 后者自 Spring Data Redis 4.0 起已弃用并标记移除
     *    （Spring Boot 4 把 Jackson 升到 3.x，包名 com.fasterxml.jackson → tools.jackson）
     * 2. 新类没有无参构造，必须用 builder() 创建；
     *    enableUnsafeDefaultTyping() 开启类型信息，等价于旧类自动写入 @class 的行为
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
     * 缓存管理器：让 @Cacheable / @CacheEvict 这类缓存注解
     * 也用 JSON 序列化（不配这个的话，注解缓存写进去还是乱码）
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