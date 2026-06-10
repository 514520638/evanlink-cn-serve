package com.evanlink.controller;

import com.evanlink.dto.*;
import com.evanlink.service.AdminAuthService;
import com.evanlink.service.ArticleService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/api/articles")
@CrossOrigin(origins = "*")
public class ArticleController {

    private static final Logger logger = LoggerFactory.getLogger(ArticleController.class);
    private static final int DEFAULT_PAGE_SIZE = 10;

    @Autowired
    private ArticleService articleService;

    @Autowired
    private AdminAuthService adminAuthService;

    @GetMapping
    public ResponseEntity<PageResponse<ArticleResponse>> searchArticles(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String tagIds,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "" + DEFAULT_PAGE_SIZE) int pageSize
    ) {
        return ResponseEntity.ok(articleService.searchArticles(keyword, categoryId, parseIds(tagIds), status, page, pageSize));
    }

    @GetMapping("/filters")
    public ResponseEntity<ArticleFiltersResponse> getFilters() {
        return ResponseEntity.ok(articleService.getFilters());
    }

    @GetMapping("/categories")
    public ResponseEntity<List<ArticleCategoryResponse>> getCategories() {
        return ResponseEntity.ok(articleService.getCategories());
    }

    @GetMapping("/tags")
    public ResponseEntity<List<TagResponse>> getTags() {
        return ResponseEntity.ok(articleService.getTags());
    }

    @GetMapping("/{slug}")
    public ResponseEntity<ArticleResponse> getArticle(@PathVariable String slug) {
        try {
            return ResponseEntity.ok(articleService.getBySlug(slug));
        } catch (NoSuchElementException ex) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping
    public ResponseEntity<?> createArticle(
            @RequestHeader(name = "Authorization", required = false) String authorization,
            @RequestBody ArticleSaveRequest request
    ) {
        if (!adminAuthService.isValidAuthorization(authorization)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Collections.singletonMap("message", "请先登录后再管理文章"));
        }
        try {
            return ResponseEntity.ok(articleService.create(request));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Collections.singletonMap("message", ex.getMessage()));
        } catch (Exception ex) {
            logger.error("Create article failed", ex);
            return ResponseEntity.internalServerError().body(Collections.singletonMap("message", "保存文章失败"));
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateArticle(
            @RequestHeader(name = "Authorization", required = false) String authorization,
            @PathVariable Long id,
            @RequestBody ArticleSaveRequest request
    ) {
        if (!adminAuthService.isValidAuthorization(authorization)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Collections.singletonMap("message", "请先登录后再管理文章"));
        }
        try {
            return ResponseEntity.ok(articleService.update(id, request));
        } catch (NoSuchElementException ex) {
            return ResponseEntity.notFound().build();
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Collections.singletonMap("message", ex.getMessage()));
        } catch (Exception ex) {
            logger.error("Update article failed, id={}", id, ex);
            return ResponseEntity.internalServerError().body(Collections.singletonMap("message", "保存文章失败"));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteArticle(
            @RequestHeader(name = "Authorization", required = false) String authorization,
            @PathVariable Long id
    ) {
        if (!adminAuthService.isValidAuthorization(authorization)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        try {
            articleService.delete(id);
            return ResponseEntity.noContent().build();
        } catch (NoSuchElementException ex) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/{slug}/view")
    public ResponseEntity<Void> incrementViews(@PathVariable String slug) {
        articleService.incrementViews(slug);
        return ResponseEntity.noContent().build();
    }

    private List<Long> parseIds(String raw) {
        if (raw == null || raw.isBlank()) {
            return Collections.emptyList();
        }
        return Arrays.stream(raw.split(","))
            .map(String::trim)
            .filter(item -> !item.isEmpty())
            .map(Long::valueOf)
            .toList();
    }
}
