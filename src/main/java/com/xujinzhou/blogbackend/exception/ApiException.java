package com.xujinzhou.blogbackend.exception;

/**
 * 通用业务异常：自带 HTTP 状态码。
 *
 * 为什么要统一成一个类？
 *   如果每种情况建一个类（ArticleNotFound / CommentNotFound / Unauthorized...），
 *   GlobalExceptionHandler 就要为每个类写一个方法，异常类会越堆越多，
 *   而且它们的行为完全一样：状态码 + 消息 → 返回 ApiResponse。
 *
 * 设计取舍：
 *   优点 —— 新增一种业务错误不用改 Handler、不用建新类，
 *           只需 throw new ApiException(400, "...") ，扩展性最好
 *   缺点 —— 状态码是【运行时数字】，写错（如 999）编译不报错，
 *           要靠约定或常量约束；不像异常类型那样有编译器兜底
 *
 * 本项目采用"统一异常 + 状态码常量"的组合来平衡两者。
 */
public class ApiException extends RuntimeException {

    public static final int BAD_REQUEST = 400;
    public static final int UNAUTHORIZED = 401;
    public static final int FORBIDDEN = 403;
    public static final int NOT_FOUND = 404;

    private final int status;

    public ApiException(int status, String message) {
        super(message);
        this.status = status;
    }

    public int getStatus() {
        return status;
    }
}
