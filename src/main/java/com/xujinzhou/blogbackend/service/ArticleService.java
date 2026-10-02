package com.xujinzhou.blogbackend.service;

import com.xujinzhou.blogbackend.common.ApiResponse;
import com.xujinzhou.blogbackend.dto.CachedArticleList;
import com.xujinzhou.blogbackend.entity.Article;
import com.xujinzhou.blogbackend.exception.ArticleNotFoundException;
import com.xujinzhou.blogbackend.mapper.ArticleMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

@Service
public class ArticleService {

    private static final Logger log = LoggerFactory.getLogger(ArticleService.class);

    /** 文章列表的缓存 key（带版本号 v1：以后返回结构变了，升到 v2 即可让旧缓存自然废弃） */
    private static final String ARTICLE_LIST_KEY = "blog:article:list:v1";

    /** 缓存有效期：10 分钟（用 Duration 比 "10 + TimeUnit" 更不容易写错参数顺序） */
    private static final Duration ARTICLE_LIST_TTL = Duration.ofMinutes(10);

    private final ArticleMapper articleMapper;
    private final RedisTemplate<String, Object> redisTemplate;

    public ArticleService(ArticleMapper articleMapper, RedisTemplate<String, Object> redisTemplate) {
        this.articleMapper = articleMapper;
        this.redisTemplate = redisTemplate;
    }

    public Article findById(Long id) {
        Article article = articleMapper.selectById(id);   // BaseMapper自带的方法，按主键查询
        if (article == null) {
            throw new ArticleNotFoundException(id);
        }
        return article;
    }

    /**
     * 查询全部文章列表 —— 带 Redis 缓存（Cache-Aside 旁路缓存模式）
     *
     * 执行流程：
     *   ① 先查 Redis
     *        命中 → 直接返回（毫秒级）
     *        未命中 → ②查 MySQL → ③写回 Redis（设 10 分钟过期）→ ④返回
     *
     * 容错原则：Redis 只是加速器，出任何问题都降级成"当没缓存"，绝不让首页挂掉
     */
    public ApiResponse<List<Article>> findAll() {
        long start = System.currentTimeMillis();

        // ① 先查缓存
        try {
            Object cached = redisTemplate.opsForValue().get(ARTICLE_LIST_KEY);
            if (cached instanceof CachedArticleList cachedList) {
                long cost = System.currentTimeMillis() - start;
                log.info("[缓存命中] key={} 耗时={}ms", ARTICLE_LIST_KEY, cost);
                return ApiResponse.success(cachedList.getArticles());
            }
            log.info("[缓存未命中] key={}，准备查询数据库", ARTICLE_LIST_KEY);
        } catch (Exception e) {
            // Redis 读失败 → 降级：当作未命中，继续查库，保证接口可用
            log.warn("[缓存读取失败，降级查库] key={}，原因：{}", ARTICLE_LIST_KEY, e.getMessage());
        }

        // ② 查数据库
        long dbStart = System.currentTimeMillis();
        List<Article> articles = articleMapper.selectList(null);   // 传null表示不加任何过滤条件
        long dbCost = System.currentTimeMillis() - dbStart;

        // ③ 写回缓存
        try {
            redisTemplate.opsForValue().set(ARTICLE_LIST_KEY, CachedArticleList.of(articles), ARTICLE_LIST_TTL);
            log.info("[写入缓存] key={}，条数={}，TTL={}分钟", ARTICLE_LIST_KEY, articles.size(), ARTICLE_LIST_TTL.toMinutes());
        } catch (Exception e) {
            // 写缓存失败不影响本次返回，只记日志
            log.warn("[缓存写入失败] key={}，原因：{}", ARTICLE_LIST_KEY, e.getMessage());
        }

        long cost = System.currentTimeMillis() - start;
        log.info("[缓存未命中→查库返回] key={} 查库耗时={}ms 总耗时={}ms 条数={}", ARTICLE_LIST_KEY, dbCost, cost, articles.size());
        return ApiResponse.success(articles);
    }
}
