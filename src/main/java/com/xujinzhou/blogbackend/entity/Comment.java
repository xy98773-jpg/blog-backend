package com.xujinzhou.blogbackend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 文章评论实体，对应数据库表 comment。
 *
 * 设计要点（与 sql/01-create-comment.sql 一一对应）：
 *   1. articleId 外键指向 article.id，ON DELETE RESTRICT
 *   2. status 为软删标记：0=正常，1=已删除
 *   3. userId 只建索引、不建外键
 */
@TableName("comment")
public class Comment {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long articleId;
    private Long userId;
    private String content;
    private Integer status;
    private LocalDateTime createdAt;

    /**
     * 评论作者的【用户名】—— 数据库 comment 表里【没有】这一列
     *
     * @TableField(exist = false) 是关键：告诉 MyBatis-Plus"这不是表的列"，
     *   这样它自动生成的 SELECT / INSERT / UPDATE 都不会带上它。
     *   ★ 不加这个注解会怎样？
     *     insert 时 MP 会拼出 INSERT INTO comment (..., username) VALUES (...)
     *     → MySQL 报 Unknown column 'username' in 'field list'
     *
     * 为什么要这个字段：前端要显示"谁说的"，但表里只存了 user_id。
     *   把 user_id 翻译成用户名是 Service 层的事（见 CommentService.fillUsernames）。
     */
    @TableField(exist = false)
    private String username;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getArticleId() {
        return articleId;
    }

    public void setArticleId(Long articleId) {
        this.articleId = articleId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }
}