package com.xujinzhou.blogbackend.dto;

/**
 * 发表评论的请求体 —— 只包含客户端【被允许】提供的信息。
 *
 * ★ 为什么不用实体类 Comment 直接接 @RequestBody？
 *   如果用 Comment 接收，客户端就能在 JSON 里塞这些字段：
 *       {"articleId":1, "userId":1, "content":"...", "status":0, "createdAt":"..."}
 *   虽然当前 Service 只读取 content、其余字段自己设置，不会立刻出问题，
 *   但这是【把不该暴露的字段暴露在接口契约上】：
 *       ① 调用方会以为这些字段有效，写了却没用 → 困惑
 *       ② 将来有人偷懒把参数直接透传给 Service → IDOR 越权漏洞
 *   用专门的 DTO，接口契约就明确表达了"客户端只能给 content"。
 *
 * 注意：Jackson 反序列化需要【无参构造 + setter】，
 *      这也是实体类当初必须加这两个东西的同一个原因。
 */
public class CommentCreateRequest {

    private String content;

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
}