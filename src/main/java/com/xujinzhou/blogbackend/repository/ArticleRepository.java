package com.xujinzhou.blogbackend.repository;

import org.springframework.stereotype.Repository;

@Repository
public class ArticleRepository {
    public String query(Long id) {
        return "第" + id + "篇文章的标题";
    }
}
