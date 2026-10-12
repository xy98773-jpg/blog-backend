package com.xujinzhou.blogbackend.exception;

/**
 * 未认证 / 身份无效 —— 继承 ApiException，固定 HTTP 401
 */
public class UnauthorizedException extends ApiException {
    public UnauthorizedException(String message) {
        super(UNAUTHORIZED, message);
    }
}
