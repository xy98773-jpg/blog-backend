package com.xujinzhou.blogbackend.handler;

import com.xujinzhou.blogbackend.common.ApiResponse;
import com.xujinzhou.blogbackend.exception.ArticleNotFoundException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice   // = @ControllerAdvice + @ResponseBody，专门用于返回JSON的场景
public class GlobalExceptionHandler {

    // 专门捕获"文章不存在"这一种异常
    @ExceptionHandler(ArticleNotFoundException.class)
    public ApiResponse<Void> handleArticleNotFound(ArticleNotFoundException e) {
        return ApiResponse.error(404, e.getMessage());
    }

    // 兜底：捕获所有没被上面专门处理到的异常，防止直接把堆栈信息暴露给前端
    @ExceptionHandler(Exception.class)
    public ApiResponse<Void> handleAllExceptions(Exception e) {
        Throwable rootCause = e;
        while (rootCause.getCause() != null) {   // 不断往下挖，找到最底层真正的原因
            rootCause = rootCause.getCause();
        }
        return ApiResponse.error(500, "服务器内部错误: " + rootCause.getMessage());
    }
}
