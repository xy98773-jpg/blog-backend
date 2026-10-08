package com.xujinzhou.blogbackend.controller;

import com.xujinzhou.blogbackend.common.ApiResponse;
import com.xujinzhou.blogbackend.dto.PageResult;
import com.xujinzhou.blogbackend.entity.Article;
import com.xujinzhou.blogbackend.service.ArticleService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ArticleController {

    private final ArticleService articleService;

    public ArticleController(ArticleService articleService) {
        this.articleService = articleService;
    }

    /**
     * 文章详情
     * GET /articles/{id}
     */
    @GetMapping("/articles/{id}")
    public ApiResponse<Article> getArticle(@PathVariable Long id) {
        return ApiResponse.success(articleService.findById(id));
    }

    /**
     * 分页查询文章列表
     * GET /articles?page=1&size=10
     *
     * 参数说明：
     *   page  页码，从 1 开始，默认 1
     *   size  每页条数，默认 10，最大 50（超出会被 service 层限制）
     *
     * 为什么用 @RequestParam(required = false) + 默认值？
     *   这样前端不传参数也能用（GET /articles 等价于 page=1&size=10），
     *   接口更好调试，也避免前端因为漏传参数直接报 400。
     */
    @GetMapping("/articles")
    public ApiResponse<PageResult<Article>> getAllArticles(
            @RequestParam(value = "page", required = false, defaultValue = "1") long page,
            @RequestParam(value = "size", required = false, defaultValue = "10") long size) {
        return articleService.findAll(page, size);
    }

    /**
     * 修改文章
     * PUT /articles/{id}
     * Body 示例：{"title":"新标题","summary":"新摘要","content":"新正文"}
     * 成功后会自动让列表缓存失效，并清除该文章的详情缓存
     */
    @PutMapping("/articles/{id}")
    public ApiResponse<Article> updateArticle(@PathVariable Long id, @RequestBody Article article) {
        return ApiResponse.success(articleService.update(id, article));
    }

    /**
     * 新增文章
     * POST /articles
     * Body 示例：{"title":"标题","summary":"摘要","content":"正文"}
     * 注意：不要带 id，id 由数据库自增生成
     */
    @PostMapping("/articles")
    public ApiResponse<Article> createArticle(@RequestBody Article article) {
        return ApiResponse.success(articleService.create(article));
    }

    /**
     * 删除文章
     * DELETE /articles/{id}
     */
    @DeleteMapping("/articles/{id}")
    public ApiResponse<Article> deleteArticle(@PathVariable Long id) {
        return ApiResponse.success(articleService.delete(id));
    }
}
