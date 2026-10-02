package com.xujinzhou.blogbackend.exception;

public class ArticleNotFoundException extends RuntimeException {
    public ArticleNotFoundException(Long id) {
        super("文章不存在，ID: " + id);
    }
}
