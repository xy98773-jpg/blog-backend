package com.xujinzhou.blogbackend.exception;

public class CommentNotFoundException extends RuntimeException {
    public CommentNotFoundException(Long id) {
        super("评论不存在或已被删除，ID: " + id);
    }
}
