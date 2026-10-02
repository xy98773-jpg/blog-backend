package com.xujinzhou.blogbackend.common;

public class ApiResponse<T> {

    private int code;
    private String message;
    private T data;

    // 私有构造方法，不让外部随便new，强制走下面的静态方法
    private ApiResponse(int code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    // 成功时调用：success(数据)
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(200, "success", data);
    }

    // 失败时调用：error(错误码, 错误信息)
    public static <T> ApiResponse<T> error(int code, String message) {
        return new ApiResponse<>(code, message, null);
    }

    // getter（前端拿到JSON需要这些方法才能正确序列化,不能省略）
    public int getCode() { return code; }
    public String getMessage() { return message; }
    public T getData() { return data; }
}
