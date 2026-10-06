package com.xujinzhou.blogbackend.handler;

import com.xujinzhou.blogbackend.common.ApiResponse;
import com.xujinzhou.blogbackend.exception.ArticleNotFoundException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@RestControllerAdvice   // = @ControllerAdvice + @ResponseBody，专门用于返回JSON的场景
public class GlobalExceptionHandler {

    /**
     * 专门捕获"文章不存在"这一种异常
     *
     * @ResponseStatus(HttpStatus.NOT_FOUND) 的作用：
     *   默认情况下，@ExceptionHandler 方法返回的 HTTP 状态码是 200 OK，
     *   错误信息只藏在响应体的 code 字段里 —— 这不符合 REST 规范，
     *   会让 axios 拦截器、监控系统、网关日志都"看不到错误"。
     *   加上这个注解，HTTP 状态码就是真正的 404，
     *   响应体里依然保留 code=404，做到"HTTP 状态码 + 业务错误码"双保险。
     */
    @ExceptionHandler(ArticleNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiResponse<Void> handleArticleNotFound(ArticleNotFoundException e) {
        return ApiResponse.error(404, e.getMessage());
    }

    /**
     * 兜底：捕获所有没被上面专门处理到的异常，防止直接把堆栈信息暴露给前端
     * 同理，这里返回真正的 HTTP 500
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiResponse<Void> handleAllExceptions(Exception e) {
        Throwable rootCause = e;
        while (rootCause.getCause() != null) {   // 不断往下挖，找到最底层真正的原因
            rootCause = rootCause.getCause();
        }
        return ApiResponse.error(500, "服务器内部错误: " + rootCause.getMessage());
    }
}
