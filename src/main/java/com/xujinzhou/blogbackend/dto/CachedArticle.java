package com.xujinzhou.blogbackend.dto;

import com.xujinzhou.blogbackend.entity.Article;

/**
 * 单篇文章的【缓存包装】（用于解决"缓存穿透"问题）
 *
 * 为什么需要一个包装类，而不是直接缓存 Article 或者 null？
 *   Redis 的"没有这个 key"和"这个 key 的值为空"是两种状态：
 *       ① key 不存在        → 表示【没缓存过】，应该去查数据库
 *       ② key 存在但值为空   → 表示【查过了，数据库里确实没有】，直接返回 404，不再查库
 *   如果直接缓存 null，Redis 里根本存不下（key 会被当成不存在），
 *   就无法区分这两种状态，也就防不住穿透。
 *   所以用一个"壳"把 null 包起来：壳存在 = 已经查过了，里面是 null 说明数据不存在。
 *
 * 这就是【缓存空值】防穿透方案的实现要点，面试经常被追问到这个细节。
 *
 * 缓存对象四要素（和 CachedArticleList 一样，缺一不可）：
 *   ① 无参构造 ② getter ③ setter ④ 类信息可被 Jackson 记录（@class）
 */
public class CachedArticle {

    private Article article;

    // ① 无参构造：反序列化必需
    public CachedArticle() {
    }

    public CachedArticle(Article article) {
        this.article = article;
    }

    // ② getter
    public Article getArticle() {
        return article;
    }

    // ③ setter
    public void setArticle(Article article) {
        this.article = article;
    }
}
