package com.xujinzhou.blogbackend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("article")   // 对应数据库里的表名
public class Article {

    @TableId(type = IdType.AUTO)   // 对应主键，AUTO表示用数据库自增策略
    private Long id;

    private String title;
    private String summary;
    private String content;
    private LocalDateTime createdAt;

    /**
     * 评论数 —— 数据库 article 表里【没有】这一列，是查询时临时算出来塞进去的
     *
     * @TableField(exist = false) 告诉 MyBatis-Plus：
     *   "这个字段不是表里的列，生成 SQL 时【不要】包含它"
     * 不加这个注解的后果：
     *   查询时 → SELECT ... , comment_count FROM article → Unknown column 'comment_count'
     *   插入时 → INSERT INTO article (..., comment_count) → 同样报错
     */
    @TableField(exist = false)
    private Integer commentCount;

    // getter/setter（Jackson序列化成JSON、MyBatis-Plus读写数据库都依赖这些方法）
    public Integer getCommentCount() { return commentCount; }
    public void setCommentCount(Integer commentCount) { this.commentCount = commentCount; }
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}