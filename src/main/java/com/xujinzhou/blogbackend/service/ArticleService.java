package com.xujinzhou.blogbackend.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xujinzhou.blogbackend.common.ApiResponse;
import com.xujinzhou.blogbackend.dto.CachedArticle;
import com.xujinzhou.blogbackend.dto.PageResult;
import com.xujinzhou.blogbackend.entity.Article;
import com.xujinzhou.blogbackend.exception.ArticleNotFoundException;
import com.xujinzhou.blogbackend.mapper.ArticleMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class ArticleService {

    private static final Logger log = LoggerFactory.getLogger(ArticleService.class);

    // ==================== 缓存 key 与 TTL ====================

    /**
     * 分页缓存 key 前缀：blog:article:list:v2:{版本号}:{页码}:{每页条数}
     *   例：blog:article:list:v2:3:1:10
     *
     * 关于 v2：第九站用的是 v1（缓存整个 List），改成"分页结构"后格式不兼容，
     *         升版本号让旧缓存自然废弃 —— 这就是当初 key 里带版本号的用处。
     */
    private static final String ARTICLE_LIST_PAGE_PREFIX = "blog:article:list:v2:";

    /**
     * 列表缓存的【版本号】key —— 这是解决"分页缓存怎么批量失效"的关键
     *
     * 问题：改成多页缓存后，缓存里有 ...:1:10、...:2:5、...:1:50 等等很多 key，
     *      新增一篇文章时怎么让它们【全部失效】？
     *
     * 笨办法：用 SCAN 扫出所有 blog:article:list:v2:* 再逐个删
     *        → 代码绕、性能差、删除过程非原子
     *
     * 本项目的办法：把版本号嵌进 key，写操作时只把版本号 +1
     *       INCR  blog:article:list:ver   （3 → 4）
     *       → 下次读缓存算出来的 key 变成 ...:4:1:10
     *       → 旧 key ...:3:1:10 再也访问不到（逻辑上已失效）
     *       → 旧 key 靠 TTL 自然过期，不需要手动清理
     *
     * 优点：一次 INCR（O(1)）就让整个逻辑缓存失效，且天然避免
     *      "删到一半失败"导致的部分脏数据。这是个值得记住的缓存技巧。
     */
    private static final String ARTICLE_LIST_VERSION_KEY = "blog:article:list:ver";

    /** 单篇文章缓存 key 前缀（拼接文章 id，如 blog:article:detail:v1:1） */
    private static final String ARTICLE_DETAIL_PREFIX = "blog:article:detail:v1:";

    /** 列表缓存基础有效期：10 分钟（TTL 是缓存一致性的最后一道兜底） */
    private static final Duration ARTICLE_LIST_TTL = Duration.ofMinutes(10);

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
     * 不加抖动的话，所有 key 的 TTL 都是整齐的 10 分钟，
     * 同一批写入的 key 会在【同一秒】集体失效，请求瞬间全涌向数据库。
     * 加 0~60 秒随机偏移后，过期时间被打散，压力被摊平。
     *
     * 实测：连续写 4 次，TTL 分别是 612 / 627 / 631 / 643 秒。
     */
    private static final long TTL_JITTER_SECONDS = 60;

    // ==================== 分页参数 ====================

    /** 默认每页条数 */
    private static final int DEFAULT_PAGE_SIZE = 10;

    /**
     * 每页最大条数
     * 防止前端传 size=100000 把数据库和内存拖垮（这是一种常见的接口滥用/攻击方式）
     */
    private static final int MAX_PAGE_SIZE = 50;

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
     *   ② MySQL 查到 → 写缓存（10 分钟 + 抖动）→ 返回
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
     * 分页查询文章列表 —— 带 Redis 缓存
     *
     * 执行流程：
     *   ① 参数纠偏（页码从 1 开始、每页条数限制在 1~50）
     *   ② 读缓存版本号，拼出本次的缓存 key
     *   ③ 查缓存：命中直接返回；未命中 → 查数据库（分页）→ 写回缓存 → 返回
     *
     * 缓存 key 形如：blog:article:list:v2:{版本号}:{页码}:{条数}
     *   —— 为什么带页码和条数？因为每页数据不同，key 必须能区分
     *   —— 为什么带版本号？因为写操作后要能"一次性"让所有分页缓存失效
     *
     * 容错原则：Redis 只是加速器，出任何问题都降级成"当没缓存"，绝不让首页挂掉
     */
    public ApiResponse<PageResult<Article>> findAll(long page, long size) {
        // ① 参数纠偏（防御性编程：不信任客户端传来的参数）
        long safePage = page < 1 ? 1 : page;
        long safeSize = size < 1 ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);

        long start = System.currentTimeMillis();

        // ② 拼缓存 key（含版本号）
        String key;
        try {
            key = ARTICLE_LIST_PAGE_PREFIX + currentCacheVersion() + ":" + safePage + ":" + safeSize;
        } catch (Exception e) {
            // 连版本号都读不到（Redis 挂了）→ 用一个固定版本号，让后面的读写都走缓存异常降级
            log.warn("[读取缓存版本号失败] 原因：{}", e.getMessage());
            key = ARTICLE_LIST_PAGE_PREFIX + "0:" + safePage + ":" + safeSize;
        }

        // ③ 查缓存
        try {
            Object cached = redisTemplate.opsForValue().get(key);
            if (cached instanceof PageResult<?> cachedPage) {
                @SuppressWarnings("unchecked")
                PageResult<Article> result = (PageResult<Article>) cachedPage;
                log.info("[缓存命中] key={} 耗时={}ms 本页条数={}",
                        key, System.currentTimeMillis() - start,
                        result.getList() == null ? 0 : result.getList().size());
                return ApiResponse.success(result);
            }
            log.info("[缓存未命中] key={}，准备查询数据库", key);
        } catch (Exception e) {
            log.warn("[缓存读取失败，降级查库] key={}，原因：{}", key, e.getMessage());
        }

        // ④ 查数据库（分页）
        long dbStart = System.currentTimeMillis();
        Page<Article> mpPage = new Page<>(safePage, safeSize);
        // selectPage 会执行两条 SQL：SELECT COUNT(*) 查总数 + SELECT ... LIMIT 查当页数据
        Page<Article> mpResult = articleMapper.selectPage(mpPage, null);
        long dbCost = System.currentTimeMillis() - dbStart;

        PageResult<Article> pageResult = PageResult.of(mpResult);

        // ⑤ 写回缓存（TTL 带随机抖动）
        try {
            Duration ttl = jitterTtl(ARTICLE_LIST_TTL);
            redisTemplate.opsForValue().set(key, pageResult, ttl);
            log.info("[写入缓存] key={} 本页条数={} total={} TTL={}秒（含随机抖动）",
                    key, pageResult.getList().size(), pageResult.getTotal(), ttl.toSeconds());
        } catch (Exception e) {
            log.warn("[缓存写入失败] key={}，原因：{}", key, e.getMessage());
        }

        log.info("[缓存未命中→查库返回] key={} 查库耗时={}ms 总耗时={}ms 本页条数={} 总条数={}",
                key, dbCost, System.currentTimeMillis() - start,
                pageResult.getList().size(), pageResult.getTotal());
        return ApiResponse.success(pageResult);
    }

    // ==================== 写操作（数据库 + 缓存，两个都要管） ====================

    /**
     * 新增文章
     *
     * 关键设计：【先插入数据库，再让缓存失效】（和 update 同一个顺序）
     *
     * 为什么返回 Article 而不返回 void？
     *   前端需要拿到【数据库生成的自增 id】，才能跳转到新文章的详情页。
     *
     * ⚠️ 必须先把 id 置空：
     *   如果请求体里带了 id（如 {"id":999,"title":"..."}），
     *   不置空的话 MyBatis-Plus 会按这个 id 插入，可能覆盖已有数据或报主键冲突。
     *   id 应该由数据库自增生成，不接受客户端指定。
     */
    public Article create(Article article) {
        article.setId(null);                  // ① 强制让数据库生成自增 id
        articleMapper.insert(article);        // ② 插入数据库（MyBatis-Plus 会把生成的 id 回填）
        evictArticleCache("新增文章", article.getId());   // ③ 再让缓存失效
        return article;
    }

    /**
     * 修改文章
     *
     * 边界处理：文章不存在时不改数据，直接抛异常。
     * 注意：这种情况下【也要清一次缓存】—— 因为详情接口会缓存"不存在"这个事实（防穿透），
     *      万一这条文章刚被创建，缓存里的空值就成了脏数据，必须先清掉。
     */
    public Article update(Long id, Article article) {
        Article existing = articleMapper.selectById(id);
        if (existing == null) {
            evictArticleCache("修改失败-文章不存在", id);
            throw new ArticleNotFoundException(id);
        }
        article.setId(id);                    // 防止请求体里带了别的 id，把数据改错行
        articleMapper.updateById(article);    // ① 先更新数据库
        evictArticleCache("修改文章", id);     // ② 再让缓存失效（列表 + 该篇详情）
        return articleMapper.selectById(id);  // 返回改完之后的最新数据
    }

    /**
     * 删除文章
     *
     * 边界处理：文章不存在时抛异常，不执行删除（同样要清一次缓存，理由同 update）
     */
    public Article delete(Long id) {
        Article existing = articleMapper.selectById(id);
        if (existing == null) {
            evictArticleCache("删除失败-文章不存在", id);
            throw new ArticleNotFoundException(id);
        }
        articleMapper.deleteById(id);         // ① 先删数据库
        evictArticleCache("删除文章", id);     // ② 再让缓存失效
        return existing;                      // 返回被删掉的那一篇（含标题，方便前端提示）
    }

    // ==================== 私有工具方法 ====================

    /**
     * 读取列表缓存的当前版本号（不存在时初始化为 1）
     *
     * 用 Redis 的 INCR 做一个自增计数器，这是 Redis 最常用的原子操作之一。
     * 这里只读不增（incrementAndGet 在写操作里调用）。
     */
    private long currentCacheVersion() {
        Object v = redisTemplate.opsForValue().get(ARTICLE_LIST_VERSION_KEY);
        if (v == null) {
            return 1L;
        }
        try {
            return Long.parseLong(v.toString());
        } catch (NumberFormatException e) {
            log.warn("[缓存版本号格式异常，重置为 1] 实际值={}", v);
            return 1L;
        }
    }

    /**
     * 让文章相关缓存失效
     *
     * 做了两件事：
     *   ① 把列表缓存【版本号 +1】→ 所有分页缓存 key 都变了 → 逻辑上全部失效
     *   ② 删掉该文章的详情缓存（如果存在）
     *
     * 【为什么必须"先库后缓存"】
     *   错误顺序（先删缓存，再更新库）：
     *       删缓存 → 【并发读进来，查库拿到旧值，写回缓存】→ 更新数据库
     *       → 缓存里留下旧值，脏了！（窗口很小，但高并发下必然发生）
     *
     *   正确顺序（先更新库，再删缓存）：
     *       更新库 → 让缓存失效
     *       → 最坏情况只是"失效前的极短窗口内有人读到旧缓存"，影响极小
     *       → 即使失效失败，也只是缓存留旧值，TTL 到期自愈
     *
     * 【为什么用"删除/失效"而不是"更新缓存新值"】
     *   ① 失效失败可自愈（缓存没了，下次查库重建）；更新失败会留下旧值，一直脏到过期
     *   ② 避免无效更新：改了 10 次但没人访问，更新缓存就是白写 10 次；
     *      失效只做 1 次，数据在下次被读时才重建（懒加载思想）
     *   ③ 并发下更新缓存可能出现旧值覆盖新值
     *
     * @param action 动作描述（只用于日志，方便排查是哪次写操作清的缓存）
     * @param articleId 文章 id（用于清除该文章的详情缓存；可为 null 表示只清列表）
     */
    private void evictArticleCache(String action, Long articleId) {
        // ① 列表缓存：版本号 +1，所有分页 key 逻辑失效
        try {
            Long newVersion = redisTemplate.opsForValue().increment(ARTICLE_LIST_VERSION_KEY);
            log.info("[清缓存-列表] 动作={} 版本号自增为={}（所有分页缓存 key 已失效）", action, newVersion);
        } catch (Exception e) {
            // 清缓存失败不能影响业务成功（数据已经写进数据库了），靠 TTL 到期自愈
            log.warn("[清缓存-列表失败] 动作={}，原因：{}（缓存将在 TTL 到期后自愈）", action, e.getMessage());
        }

        // ② 详情缓存：直接删除该文章的 key
        if (articleId != null) {
            try {
                Boolean removed = redisTemplate.delete(ARTICLE_DETAIL_PREFIX + articleId);
                log.info("[清缓存-详情] 动作={} key={} 是否删掉了={}",
                        action, ARTICLE_DETAIL_PREFIX + articleId, removed);
            } catch (Exception e) {
                log.warn("[清缓存-详情失败] 动作={} id={}，原因：{}", action, articleId, e.getMessage());
            }
        }
    }

    /**
     * 给 TTL 加上随机抖动，避免大量 key 在同一时刻集体失效（防缓存雪崩）
     *
     * @param base 基础有效期
     * @return base + random(0, TTL_JITTER_SECONDS) 秒
     *
     * 为什么用 ThreadLocalRandom 而不是 Random？
     *   ThreadLocalRandom 是 JDK 7 引入的，高并发下比共用一个 Random 实例性能更好
     *   （多个线程共用一个 Random 时会有 CAS 竞争）
     */
    private Duration jitterTtl(Duration base) {
        long extra = ThreadLocalRandom.current().nextLong(TTL_JITTER_SECONDS + 1);   // 0 ~ 60
        return base.plusSeconds(extra);
    }
}
