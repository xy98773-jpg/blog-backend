package com.xujinzhou.blogbackend.controller;

import com.xujinzhou.blogbackend.common.ApiResponse;
import com.xujinzhou.blogbackend.dto.CommentCreateRequest;
import com.xujinzhou.blogbackend.dto.PageResult;
import com.xujinzhou.blogbackend.entity.Comment;
import com.xujinzhou.blogbackend.service.CommentService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CommentController {

    private final CommentService commentService;

    public CommentController(CommentService commentService) {
        this.commentService = commentService;
    }

    /**
     * 查某篇文章的评论列表（分页，无需登录）
     * GET /articles/{id}/comments?page=1&size=10
     */
    @GetMapping("/articles/{id}/comments")
    public ApiResponse<PageResult<Comment>> listComments(
            @PathVariable Long id,
            @RequestParam(value = "page", required = false, defaultValue = "1") long page,
            @RequestParam(value = "size", required = false, defaultValue = "10") long size) {
        return ApiResponse.success(commentService.listByArticle(id, page, size));
    }

    /**
     * 发表评论（需要登录）
     * POST /articles/{id}/comments
     * Body 示例：{"content":"说得好"}
     *
     * ★ 注意 @AuthenticationPrincipal String username 这个参数
     *   —— username 来自 JWT，不由客户端提供，也不出现在请求体里
     */
    @PostMapping("/articles/{id}/comments")
    public ApiResponse<Comment> createComment(@PathVariable Long id,
                                              @AuthenticationPrincipal String username,
                                              @RequestBody CommentCreateRequest request) {
        String content = request == null ? null : request.getContent();
        return ApiResponse.success(commentService.create(id, username, content));
    }

    /**
     * 删除评论（需要登录）—— 实际是软删（status 置为 1）
     * DELETE /comments/{id}
     *
     * 为什么路径不放在 /articles/{articleId}/comments/{commentId} 下面？
     *   因为删除操作只需要评论 id 就能完成，articleId 是冗余信息，
     *   放进 URL 反而要额外校验"这个评论是否真属于这篇文章"，增加复杂度。
     *   —— REST 路径设计的一个原则：URL 只表达【标识资源所必需】的信息。
     */
    @DeleteMapping("/comments/{id}")
    public ApiResponse<Comment> deleteComment(@PathVariable Long id,
                                              @AuthenticationPrincipal String username) {
        return ApiResponse.success(commentService.delete(id, username));
    }
}
