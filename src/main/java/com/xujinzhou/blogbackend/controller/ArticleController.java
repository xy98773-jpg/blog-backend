package com.xujinzhou.blogbackend.controller;
import java.util.List;
import com.xujinzhou.blogbackend.common.ApiResponse;
import com.xujinzhou.blogbackend.entity.Article;
import com.xujinzhou.blogbackend.service.ArticleService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

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

}
