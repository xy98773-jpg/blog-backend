package com.xujinzhou.blogbackend.controller;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/redis-test")
public class RedisTestController {

    private final RedisTemplate<String, Object> redisTemplate;

    // 构造方法注入（和之前 UserService 一样的写法）
    public RedisTestController(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 写一个 key 进 Redis，10 分钟后自动过期
     * 访问 http://localhost:18080/redis-test/set
     */
    @GetMapping("/set")
    public String set() {
        redisTemplate.opsForValue().set("blog:hello", "来自 Spring Boot 的第一条缓存", 10, TimeUnit.MINUTES);
        return "已写入 Redis，key = blog:hello";
    }

    /**
     * 从 Redis 读出来
     * 访问 http://localhost:18080/redis-test/get
     */
    @GetMapping("/get")
    public Object get() {
        return redisTemplate.opsForValue().get("blog:hello");
    }
}
