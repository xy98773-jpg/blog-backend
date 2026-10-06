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

    /** 缓存有效期：10 分钟（TTL 是缓存一致性的最后一道兜底） */
    private static final Duration ARTICLE_LIST_TTL = Duration.ofMinutes(10);

    private final ArticleMapper articleMapper;
    private final RedisTemplate<String, Object> redisTemplate;

    public ArticleService(ArticleMapper articleMapper, RedisTemplate<String, Object> redisTemplate) {
        this.articleMapper = articleMapper;
        this.redisTemplate = redisTemplate;
    }

    // ==================== 读操作 ====================

    public Article findById(Long id) {
        Article article = articleMapper.selectById(id);   // BaseMapper自带的方法，按主键查询
        if (article == null) {
            throw new ArticleNotFoundException(id);
        }
        return article;
    }

    /**
     * 查询全部文章列表 —— Cache-Aside（旁路缓存）模式
     *
     * 执行流程：
     *   ① 先查 Redis：命中直接返回
     *   ② 未命中：查 MySQL → 写回 Redis（10 分钟）→ 返回
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

    // ==================== 写操作（数据库 + 缓存，两个都要管） ====================

    /**
     * 修改文章
     *
     * 关键设计：【先更新数据库，再删除缓存】
     *   为什么是这个顺序？见下方 evictArticleListCache 的注释
     *
     * 边界处理：文章不存在时不改数据，直接抛异常（不删缓存，因为什么都没改）
     */
    public Article update(Long id, Article article) {
        Article existing = articleMapper.selectById(id);
        if (existing == null) {
            throw new ArticleNotFoundException(id);
        }
        article.setId(id);                    // 防止请求体里带了别的 id，把数据改错行
        articleMapper.updateById(article);    // ① 先更新数据库
        evictArticleListCache("修改文章");     // ② 再让缓存失效
        return articleMapper.selectById(id);  // 返回改完之后的最新数据
    }

    /**
     * 让文章列表缓存失效（统一走这里，避免每个写方法各写一遍）
     *
     * 【为什么必须"先库后缓存"】
     *   错误顺序（先删缓存，再更新库）：
     *       删缓存 → 【并发读进来，查库拿到旧值，写回缓存】→ 更新数据库
     *       → 缓存里留下旧值，脏了！（窗口很小，但高并发下必然发生）
     *
     *   正确顺序（先更新库，再删缓存）：
     *       更新库 → 删缓存
     *       → 最坏情况只是"删除前的极短窗口内有人读到旧缓存"，影响极小
     *       → 即使删缓存失败，也只是缓存留旧值，TTL 到期自愈
     *
     * 【为什么用 delete 而不是 set(新值)】
     *   ① 删除失败可自愈（缓存没了，下次查库重建）
     *      更新失败会留下旧值，一直脏到过期
     *   ② 避免无效更新：改了 10 次但没人访问，更新缓存就是白写 10 次；
     *      删除只写 1 次，数据在下次被读时才重建（懒加载思想）
     *   ③ 并发下更新缓存可能出现旧值覆盖新值
     */
    private void evictArticleListCache(String action) {
        try {
            Boolean removed = redisTemplate.delete(ARTICLE_LIST_KEY);
            log.info("[清缓存] 动作={} key={} 是否删掉了={}", action, ARTICLE_LIST_KEY, removed);
        } catch (Exception e) {
            // 删缓存失败不能影响业务成功（数据已经写进数据库了），靠 TTL 到期自愈
            log.warn("[清缓存失败] 动作={} key={}，原因：{}（缓存将在 TTL 到期后自愈）",
                    action, ARTICLE_LIST_KEY, e.getMessage());
        }
    }
}