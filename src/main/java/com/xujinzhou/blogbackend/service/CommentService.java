package com.xujinzhou.blogbackend.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xujinzhou.blogbackend.dto.PageResult;
import com.xujinzhou.blogbackend.entity.Comment;
import com.xujinzhou.blogbackend.entity.User;
import com.xujinzhou.blogbackend.exception.ApiException;
import com.xujinzhou.blogbackend.exception.UnauthorizedException;
import com.xujinzhou.blogbackend.mapper.ArticleMapper;
import com.xujinzhou.blogbackend.mapper.CommentMapper;
import com.xujinzhou.blogbackend.mapper.UserMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class CommentService {

    private static final Logger log = LoggerFactory.getLogger(CommentService.class);

    /** 评论状态：正常 */
    private static final int STATUS_NORMAL = 0;
    /** 评论状态：已删除（软删，不物理删行） */
    private static final int STATUS_DELETED = 1;

    /** 默认每页条数（与文章列表保持一致） */
    private static final int DEFAULT_PAGE_SIZE = 10;
    /** 每页最大条数（防止 size=100000 拖垮数据库） */
    private static final int MAX_PAGE_SIZE = 50;

    /** 内容长度上限，与建表语句 varchar(500) 对应 */
    private static final int MAX_CONTENT_LENGTH = 500;

    private final CommentMapper commentMapper;
    private final ArticleMapper articleMapper;
    private final UserMapper userMapper;

    /**
     * 只为了一件事：发 / 删评论后让【文章列表】缓存失效。
     * 列表里显示的"评论数"是那份缓存的一部分，不清的话首页数字永远是旧的。
     * （ArticleService 不依赖 CommentService，所以这里是单向依赖，不会循环依赖）
     */
    private final ArticleService articleService;

    public CommentService(CommentMapper commentMapper, ArticleMapper articleMapper,
                          UserMapper userMapper, ArticleService articleService) {
        this.commentMapper = commentMapper;
        this.articleMapper = articleMapper;
        this.userMapper = userMapper;
        this.articleService = articleService;
    }

    // ==================== 读操作 ====================

    /**
     * 查某篇文章的评论列表（分页）—— 只返回未被软删的评论
     */
    public PageResult<Comment> listByArticle(Long articleId, long page, long size) {
        long safePage = page < 1 ? 1 : page;
        long safeSize = size < 1 ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);

        requireArticleExists(articleId);

        QueryWrapper<Comment> wrapper = new QueryWrapper<>();
        wrapper.eq("article_id", articleId)
                .eq("status", STATUS_NORMAL)
                .orderByDesc("id");

        Page<Comment> mpPage = new Page<>(safePage, safeSize);
        Page<Comment> mpResult = commentMapper.selectPage(mpPage, wrapper);

        log.info("[查评论列表] articleId={} page={} size={} 本页条数={} 总数={}",
                articleId, safePage, safeSize, mpResult.getRecords().size(), mpResult.getTotal());

        return PageResult.of(mpResult);
    }

    // ==================== 写操作 ====================

    /**
     * 发表评论
     *
     * @param username 当前登录用户名（来自 JWT / SecurityContext，不是请求参数）
     *                 —— 故意不接收 userId：客户端绝不允许自己指定"我以谁的身份评论"
     */
    public Comment create(Long articleId, String username, String content) {
        requireArticleExists(articleId);

        if (content == null || content.isBlank()) {
            throw new ApiException(ApiException.BAD_REQUEST, "评论内容不能为空");
        }
        if (content.length() > MAX_CONTENT_LENGTH) {
            throw new ApiException(ApiException.BAD_REQUEST,
                    "评论内容超长，最多 " + MAX_CONTENT_LENGTH + " 个字符");
        }

        Long userId = resolveUserId(username);

        Comment comment = new Comment();
        comment.setArticleId(articleId);
        comment.setUserId(userId);
        comment.setContent(content);
        comment.setStatus(STATUS_NORMAL);

        commentMapper.insert(comment);

        // ★ 列表里的"评论数"变了 —— 必须让文章列表缓存失效，否则首页永远显示旧数字
        articleService.evictArticleListCache("发表评论 articleId=" + articleId);

        log.info("[发表评论] commentId={} articleId={} userId={} contentLength={}",
                comment.getId(), articleId, userId, content.length());

        return comment;
    }

    /**
     * 删除评论 —— 软删（只改 status，不删行）
     *
     * ★ 必须校验【评论归属】：只有作者本人能删自己的评论。
     *   缺这道校验 = 水平越权漏洞（任何登录用户能删任何人的评论）。
     *
     * @param username 当前登录用户名（来自 JWT），不是请求参数 —— 客户端无法伪造
     */
    public Comment delete(Long commentId, String username) {
        Comment existing = commentMapper.selectById(commentId);

        // ① 评论不存在 / 已被软删 → 404
        if (existing == null || existing.getStatus() == STATUS_DELETED) {
            throw new ApiException(ApiException.NOT_FOUND, "评论不存在或已被删除，ID: " + commentId);
        }

        // ② 解析当前用户，并校验归属 → 不是自己的评论返回 403
        Long currentUserId = resolveUserId(username);
        if (!existing.getUserId().equals(currentUserId)) {
            log.warn("[越权删除被拒绝] commentId={} 评论作者 userId={} 当前用户 userId={} username={}",
                    commentId, existing.getUserId(), currentUserId, username);
            throw new ApiException(ApiException.FORBIDDEN, "无权删除他人的评论");
        }

        // ③ 执行软删
        //    注意 WHERE 里【同时】带 status 和 user_id：
        //      · status：CAS 思想，防止并发重复删同一条
        //      · user_id：把权限校验下沉到 SQL，即使上面的 Java 判断被绕过也删不掉别人的
        UpdateWrapper<Comment> wrapper = new UpdateWrapper<>();
        wrapper.eq("id", commentId)
                .eq("status", STATUS_NORMAL)
                .eq("user_id", currentUserId)
                .set("status", STATUS_DELETED);
        int affected = commentMapper.update(null, wrapper);

        if (affected == 0) {
            // 走到这里说明：并发情况下状态刚被改掉，或归属判断和实际数据不一致
            throw new ApiException(ApiException.NOT_FOUND, "评论不存在或已被删除，ID: " + commentId);
        }

        log.info("[删除评论-软删] commentId={} 操作人={}(userId={}) 影响行数={}（数据仍在表中，status 已置为 {}）",
                commentId, username, currentUserId, affected, STATUS_DELETED);

        // 软删同样是"评论数 -1"，列表缓存一样要失效
        articleService.evictArticleListCache("删除评论 articleId=" + existing.getArticleId());

        existing.setStatus(STATUS_DELETED);
        return existing;
    }

    // ==================== 私有工具方法 ====================

    /**
     * 校验文章是否存在 —— 评论不能挂在不存在的文章上
     *
     * 这一层校验的真正价值（不只是"多查一次库"）：
     *   ① 数据库外键约束拒绝时抛的是 SQLIntegrityConstraintViolationException，
     *      会被兜底成 HTTP 500 —— 但这明明是客户端请求了不存在的文章，应该是 404
     *   ② 数据库报错内容是 "foreign key constraint fails"，对调用方毫无意义
     *   → Java 层先拦，才能给出【正确的状态码 + 可读的消息】
     *   → 数据库外键是最后一道保险，永远不要依赖数据库报错做业务判断
     */
    private void requireArticleExists(Long articleId) {
        if (articleMapper.selectById(articleId) == null) {
            throw new ApiException(ApiException.NOT_FOUND, "文章不存在，ID: " + articleId);
        }
    }

    /**
     * 把【用户名】解析成【用户 id】
     *
     * 为什么需要这一步？
     *   JwtUtil 签发的 Token 里只存了 username（作为 subject），没有 userId，
     *   而 comment 表存的是 user_id (bigint) —— 必须转换。
     *
     * 安全性说明（★ 很重要）：
     *   userId 必须从这里推导，【绝不能】从请求体里接收。
     *   否则任何人只要改一下 JSON 里的 userId，就能以别人的身份发评论。
     *   这类漏洞叫 IDOR（Insecure Direct Object Reference，越权访问），
     *   是最常见的 Web 漏洞之一。
     */
    private Long resolveUserId(String username) {
        if (username == null || username.isBlank()) {
            throw new UnauthorizedException("未获取到登录信息，请先登录");
        }

        QueryWrapper<User> wrapper = new QueryWrapper<>();
        wrapper.eq("username", username);
        User user = userMapper.selectOne(wrapper);

        if (user == null) {
            // Token 签名有效、没过期，但账号在数据库里已不存在（例如被删号）
            throw new UnauthorizedException("登录用户不存在或已失效，请重新登录");
        }

        log.info("[解析当前用户] username={} → userId={}", username, user.getId());
        return user.getId();
    }
}