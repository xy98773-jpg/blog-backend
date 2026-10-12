package com.xujinzhou.blogbackend.handler;

import com.xujinzhou.blogbackend.common.ApiResponse;
import com.xujinzhou.blogbackend.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
@RestControllerAdvice   // = @ControllerAdvice + @ResponseBody，专门用于返回JSON的场景
public class GlobalExceptionHandler {

    /**
     * 统一处理所有业务异常（ApiException 及其子类）
     *
     * 【为什么这里不能用 @ResponseStatus】
     *   @ResponseStatus 的值必须是【编译期常量】（注解的值不能是运行时变量），
     *   而我们的状态码是 ApiException 里的实例字段（400/401/403/404 视情况而定）。
     *   → 所以改用 ResponseEntity：它的状态码可以在运行时决定。
     *
     * 用继承关系覆盖了原来的 ArticleNotFoundException（404）、
     * CommentNotFoundException（404）、IllegalArgumentException（400）。
     */
    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiResponse<Void>> handleApiException(ApiException e) {
        return ResponseEntity.status(e.getStatus())
                .body(ApiResponse.error(e.getStatus(), e.getMessage()));
    }

    /**
     * 路由不存在：没有任何 @RequestMapping 能匹配这个路径
     *   → 本质是【客户端请求了不存在的地址】，必须是 404，不是 500
     *
     * 不加这个方法会怎样？
     *   → 被下面的 Exception 兜底捕获 → 返回 500
     *   → 前端会以为"后端崩了"，实际只是路径写错
     *
     * 注意 e.getMessage() 的原始内容是：
     *   "No static resource articles/99999/comments for request '/articles/99999/comments'."
     *   —— 这是给开发者看的框架内部描述，直接返回给客户端不够清晰，
     *      所以这里自己拼一条更直白的消息。
     */
    @ExceptionHandler(NoResourceFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiResponse<Void> handleNoResourceFound(NoResourceFoundException e) {
        return ApiResponse.error(404, "接口不存在：" + e.getResourcePath());
    }

    /**
     * HTTP 方法不被支持：路径存在，但用的方法不对
     *   例：GET /hello 存在，用 POST /hello 调用 → 405 Method Not Allowed
     *
     * 这也属于客户端错误（4xx），不该报 500
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    @ResponseStatus(HttpStatus.METHOD_NOT_ALLOWED)
    public ApiResponse<Void> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        return ApiResponse.error(405,
                "请求方法 " + e.getMethod() + " 不被支持，请检查接口文档");
    }
    /**
     * URL 路径变量或查询参数【类型转换失败】
     *   例：DELETE /comments/abc  →  期望 Long，收到 "abc"
     *       GET /articles?page=xyz →  期望 long，收到 "xyz"
     *
     * 这是客户端参数格式错误，必须是 400，不能报 500
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        Class<?> requiredType = e.getRequiredType();
        String typeName = requiredType == null ? "未知类型" : requiredType.getSimpleName();
        return ApiResponse.error(400,
                "参数 " + e.getName() + " 类型错误，期望 " + typeName + "，实际值: " + e.getValue());
    }
    /**
     * 兜底：捕获所有没被上面处理到的异常，防止直接把堆栈信息暴露给前端
     * 这里状态码是固定值，可以继续用 @ResponseStatus
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