package com.xujinzhou.blogbackend.exception;

public class ArticleNotFoundException extends ApiException {
    public ArticleNotFoundException(Long id) {
        super(NOT_FOUND, "文章不存在，ID: " + id);
    }
}
