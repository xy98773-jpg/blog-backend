package com.xujinzhou.blogbackend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
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

    // getter/setter（Jackson序列化成JSON、MyBatis-Plus读写数据库都依赖这些方法）
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