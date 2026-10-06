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
import com.xujinzhou.blogbackend.dto.CachedArticle;
import java.util.concurrent.ThreadLocalRandom;
@Service
public class ArticleService {

    private static final Logger log = LoggerFactory.getLogger(ArticleService.class);

    /** 文章列表的缓存 key（带版本号 v1：以后返回结构变了，升到 v2 即可让旧缓存自然废弃） */
    private static final String ARTICLE_LIST_KEY = "blog:article:list:v1";

    /** 缓存有效期：10 分钟（TTL 是缓存一致性的最后一道兜底） */
    private static final Duration ARTICLE_LIST_TTL = Duration.ofMinutes(10);

    /** 单篇文章缓存 key 前缀（拼接文章 id，如 blog:article:detail:v1:1） */
    private static final String ARTICLE_DETAIL_PREFIX = "blog:article:detail:v1:";

    /** 单篇文章缓存有效期：10 分钟 */
    private static final Duration ARTICLE_DETAIL_TTL = Duration.ofMinutes(10);

    /**
     * "文章不存在"这个事实的缓存有效期：60 秒
     *
     * 为什么比正常数据短？
     *   万一这篇文章后来被创建了，缓存空值不能长期拦住它。
     *   所以空值的 TTL 要短，60 秒足够挡住短时间内的重复攻击。
     */
    private static final Duration ARTICLE_NULL_TTL = Duration.ofSeconds(60);

    /**
     * TTL 随机抖动上限（秒）—— 防缓存雪崩的第一道手段
     *
     * 如果不加抖动：
     *   所有 key 的 TTL 都是整齐的 10 分钟
     *   → 同一批写入的 key 会在【同一秒】集体失效
     *   → 所有请求瞬间涌向数据库（雪崩）
     *
     * 加 0~60 秒随机偏移后：
     *   过期时间被【打散】，数据库压力被摊平
     *
     * 实测：连续写 4 次，TTL 分别是 653 / 621 / 631 / 648 秒 —— 每次都不同
     */
    private static final long TTL_JITTER_SECONDS = 60;

    private final ArticleMapper articleMapper;
    private final RedisTemplate<String, Object> redisTemplate;

    public ArticleService(ArticleMapper articleMapper, RedisTemplate<String, Object> redisTemplate) {
        this.articleMapper = articleMapper;
        this.redisTemplate = redisTemplate;
    }

    // ==================== 读操作 ====================

    /**
     * 按 id 查询文章 —— 带缓存 + 防缓存穿透
     *
     * 执行流程：
     *   ① 查 Redis
     *        命中"壳"且壳里有文章 → 直接返回（毫秒级）
     *        命中"壳"但壳里是 null → 说明之前查过、数据库确实没有 → 直接抛 404，【不查库】
     *        未命中 → ②查 MySQL
     *   ② MySQL 查到 → 写缓存（10 分钟）→ 返回
     *      MySQL 没查到 → 写【空值缓存】（60 秒）→ 抛 404
     *
     * 关于"防穿透"：
     *   恶意请求一直查不存在的 id（如 999999），如果不缓存空值，
     *   每次都会穿透缓存打到数据库。缓存空值后，60 秒内的重复请求全部被挡住。
     */
    public Article findById(Long id) {
        String key = ARTICLE_DETAIL_PREFIX + id;

        // ① 查缓存
        try {
            Object cached = redisTemplate.opsForValue().get(key);
            if (cached instanceof CachedArticle cachedArticle) {
                Article article = cachedArticle.getArticle();
                if (article == null) {
                    // 命中空值缓存：数据库里确实没有这篇文章，直接抛异常，不再查库
                    log.info("[缓存命中-空值] key={}（防穿透生效，未查库）", key);
                    throw new ArticleNotFoundException(id);
                }
                log.info("[缓存命中] key={} id={}", key, id);
                return article;
            }
            log.info("[缓存未命中] key={}", key);
        } catch (ArticleNotFoundException e) {
            // 这是业务异常，必须原样抛出，不能被下面的 catch 吞掉！
            // （否则"文章不存在"会变成"降级查库"，逻辑就乱了）
            throw e;
        } catch (Exception e) {
            // Redis 读失败 → 降级：当作未命中，继续查库，保证接口可用
            log.warn("[缓存读取失败，降级查库] key={}，原因：{}", key, e.getMessage());
        }

        // ② 查数据库
        Article article = articleMapper.selectById(id);

        if (article == null) {
            // ③-a 数据库也没有 → 缓存"不存在"这个事实（短 TTL），防止反复穿透
            try {
                redisTemplate.opsForValue().set(key, new CachedArticle(null), ARTICLE_NULL_TTL);
                log.info("[写入空值缓存] key={} TTL={}秒（防穿透）", key, ARTICLE_NULL_TTL.toSeconds());
            } catch (Exception e) {
                log.warn("[空值缓存写入失败] key={}，原因：{}", key, e.getMessage());
            }
            throw new ArticleNotFoundException(id);
        }

        // ③-b 查到数据 → 写缓存（TTL 带随机抖动，防雪崩）
        try {
            Duration ttl = jitterTtl(ARTICLE_DETAIL_TTL);
            redisTemplate.opsForValue().set(key, new CachedArticle(article), ttl);
            log.info("[写入缓存] key={} id={} TTL={}秒（含随机抖动）", key, id, ttl.toSeconds());
        } catch (Exception e) {
            log.warn("[缓存写入失败] key={}，原因：{}", key, e.getMessage());
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

        // ③ 写回缓存（TTL 带随机抖动，防雪崩）
        try {
            Duration ttl = jitterTtl(ARTICLE_LIST_TTL);
            redisTemplate.opsForValue().set(ARTICLE_LIST_KEY, CachedArticleList.of(articles), ttl);
            log.info("[写入缓存] key={}，条数={}，TTL={}秒（含随机抖动）", ARTICLE_LIST_KEY, articles.size(), ttl.toSeconds());
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
     * 新增文章
     *
     * 关键设计：【先插入数据库，再删除缓存】（和 update 同一个顺序，理由见 evictArticleListCache 注释）
     *
     * 为什么返回 Article 而不返回 void？
     *   前端需要拿到【数据库生成的自增 id】，才能跳转到新文章的详情页。
     *
     * ⚠️ 注意：必须先把 id 置空！
     *   如果请求体里带了 id（如 {"id":999,"title":"..."}），
     *   不置空的话 MyBatis-Plus 会按这个 id 插入，可能覆盖已有数据或报主键冲突。
     *   id 应该由数据库自增生成，不接受客户端指定。
     */
    public Article create(Article article) {
        article.setId(null);                  // ① 强制让数据库生成自增 id，防止客户端指定
        articleMapper.insert(article);        // ② 插入数据库（MyBatis-Plus 会把生成的 id 回填到 article）
        evictArticleListCache("新增文章");     // ③ 再让缓存失效
        return article;                       // 返回带 id 的完整对象
    }
    /**
     * 删除文章
     *
     * 关键设计：【先删除数据库，再删除缓存】（和 update/create 同一个顺序）
     *
     * 边界处理：文章不存在时抛异常，不执行删除
     *
     * 为什么返回被删除的对象？
     *   前端/调用方通常需要知道"删掉了什么"（比如显示提示"已删除《xxx》"），
     *   而且先查出来再删，顺便起到"校验存在性"的作用。
     */
    public Article delete(Long id) {
        Article existing = articleMapper.selectById(id);
        if (existing == null) {
            throw new ArticleNotFoundException(id);
        }
        articleMapper.deleteById(id);         // ① 先删数据库
        evictArticleListCache("删除文章");     // ② 再让缓存失效
        return existing;                      // 返回被删掉的那一篇（含标题，方便前端提示）
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

    /**
     * 给 TTL 加上随机抖动，避免大量 key 在同一时刻集体失效（防缓存雪崩）
     *
     * @param base 基础有效期
     * @return base + random(0, TTL_JITTER_SECONDS) 秒
     *
     * 为什么用 ThreadLocalRandom 而不是 Random？
     *   ThreadLocalRandom 是 JDK 7 引入的，在高并发下比 new Random() 性能更好
     *   （多个线程共用一个 Random 实例时会有 CAS 竞争）
     */
    private Duration jitterTtl(Duration base) {
        long extra = ThreadLocalRandom.current().nextLong(TTL_JITTER_SECONDS + 1);   // 0 ~ 60
        return base.plusSeconds(extra);
    }
}