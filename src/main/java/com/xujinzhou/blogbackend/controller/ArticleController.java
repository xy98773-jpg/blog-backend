package com.xujinzhou.blogbackend.controller;
import java.util.List;
import com.xujinzhou.blogbackend.common.ApiResponse;
import com.xujinzhou.blogbackend.entity.Article;
import com.xujinzhou.blogbackend.service.ArticleService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.DeleteMapping;

@RestController
public class ArticleController {

    private final ArticleService articleService;

    public ArticleController(ArticleService articleService) {
        this.articleService = articleService;
    }

    @GetMapping("/articles/{id}")
    public ApiResponse<Article> getArticle(@PathVariable Long id) {
        Article article = articleService.findById(id);
        return ApiResponse.success(article);
    }

    // GET /articles，查全部列表（带 Redis 缓存，service 层已经包装好 ApiResponse）
    @GetMapping("/articles")
    public ApiResponse<List<Article>> getAllArticles() {
        return articleService.findAll();   // 直接返回，不要再包一层 ApiResponse
    }

    /**
     * 修改文章
     * PUT /articles/{id}
     * Body 示例：{"title":"新标题","summary":"新摘要","content":"新正文"}
     * 成功后会自动清除列表缓存
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
     * 成功后会自动清除列表缓存
     */
    @PostMapping("/articles")
    public ApiResponse<Article> createArticle(@RequestBody Article article) {
        return ApiResponse.success(articleService.create(article));
    }

    /**
     * 删除文章
     * DELETE /articles/{id}
     * 成功后会自动清除列表缓存
     */
    @DeleteMapping("/articles/{id}")
    public ApiResponse<Article> deleteArticle(@PathVariable Long id) {
        return ApiResponse.success(articleService.delete(id));
    }
}