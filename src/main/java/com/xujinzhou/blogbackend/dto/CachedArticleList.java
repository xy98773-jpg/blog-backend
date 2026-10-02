package com.xujinzhou.blogbackend.dto;

import com.xujinzhou.blogbackend.entity.Article;

import java.util.ArrayList;
import java.util.List;

/**
 * 文章列表的【缓存专用 DTO】（DTO = Data Transfer Object，数据传输对象）
 *
 * 为什么需要它？—— 解决 Java 泛型擦除问题
 *   如果直接把 List<Article> 放进 Redis，读回来只能得到 List<LinkedHashMap>，
 *   因为泛型信息在运行时被擦除了，Jackson 不知道元素该还原成什么类型。
 *   把 List 包在一个【具体类】里，类型就明确了。
 *
 * 为什么不用 ApiResponse 包？—— 同样因为泛型擦除
 *   ApiResponse<T> 里的 T 在运行时会被擦除。所以这里用一个"全能 DTO"，
 *   把 code / message / articles 三个字段平铺展开，类型全部是具体的。
 *
 * 缓存对象的四个必备条件（缺一不可）：
 *   ① 无参构造方法  —— Jackson 反序列化时先造空对象
 *   ② getter       —— Jackson 序列化时读取属性
 *   ③ setter       —— Jackson 反序列化时写入属性
 *   ④ 类信息可被记录 —— 由 RedisConfig 里的 enableUnsafeDefaultTyping() 提供（写入 @class）
 */
public class CachedArticleList {

    private int code;
    private String message;
    private List<Article> articles;

    // ① 无参构造：反序列化必需
    public CachedArticleList() {
    }

    public CachedArticleList(int code, String message, List<Article> articles) {
        this.code = code;
        this.message = message;
        this.articles = articles == null ? new ArrayList<>() : articles;
    }

    /** 便利工厂方法：从文章列表构造（默认 code=200, message="success"） */
    public static CachedArticleList of(List<Article> articles) {
        return new CachedArticleList(200, "success", articles);
    }

    // ② getter
    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public List<Article> getArticles() {
        return articles;
    }

    // ③ setter
    public void setCode(int code) {
        this.code = code;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public void setArticles(List<Article> articles) {
        this.articles = articles;
    }
}
