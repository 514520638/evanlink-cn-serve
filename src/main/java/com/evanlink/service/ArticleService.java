package com.evanlink.service;

import com.evanlink.dto.*;
import com.evanlink.model.*;
import com.evanlink.repository.*;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.text.Normalizer;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ArticleService {

    @Autowired
    private ArticleRepository articleRepository;

    @Autowired
    private ArticleCategoryRepository articleCategoryRepository;

    @Autowired
    private TagRepository tagRepository;

    @Autowired
    private ArticleTagRepository articleTagRepository;

    public PageResponse<ArticleResponse> searchArticles(
            String keyword,
            Long categoryId,
            List<Long> tagIds,
            String status,
            int page,
            int pageSize
    ) {
        int safePage = Math.max(page, 1);
        int safePageSize = Math.min(Math.max(pageSize, 1), 50);
        Pageable pageable = PageRequest.of(safePage - 1, safePageSize, Sort.by(Sort.Direction.DESC, "publishedAt", "createdAt"));
        Page<Article> result = articleRepository.findAll(buildSpec(keyword, categoryId, tagIds, status), pageable);
        List<ArticleResponse> items = result.getContent().stream()
            .map(article -> ArticleResponse.from(article, false))
            .toList();
        return new PageResponse<>(items, safePage, safePageSize, result.getTotalElements());
    }

    private Specification<Article> buildSpec(String keyword, Long categoryId, List<Long> tagIds, String status) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isFalse(root.get("deleted")));

            ArticleStatus articleStatus = parseStatus(status);
            if (articleStatus != null) {
                predicates.add(cb.equal(root.get("status"), articleStatus));
            } else {
                predicates.add(cb.equal(root.get("status"), ArticleStatus.PUBLISHED));
            }

            if (categoryId != null) {
                predicates.add(cb.equal(root.get("category").get("id"), categoryId));
            }

            if (StringUtils.hasText(keyword)) {
                String like = "%" + keyword.trim().toLowerCase(Locale.ROOT) + "%";
                predicates.add(cb.or(
                    cb.like(cb.lower(root.get("title")), like),
                    cb.like(cb.lower(root.get("titleEn")), like),
                    cb.like(cb.lower(root.get("excerpt")), like),
                    cb.like(cb.lower(root.get("excerptEn")), like),
                    cb.like(cb.lower(root.get("content")), like)
                ));
            }

            if (tagIds != null && !tagIds.isEmpty()) {
                Join<Article, ArticleTag> tagJoin = root.join("articleTags", JoinType.INNER);
                predicates.add(tagJoin.get("tag").get("id").in(tagIds));
                query.groupBy(root.get("id"));
                query.having(cb.equal(cb.countDistinct(tagJoin.get("tag").get("id")), (long) tagIds.size()));
            }

            query.distinct(true);
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    public ArticleResponse getBySlug(String slug) {
        Article article = articleRepository.findBySlugAndDeletedFalse(slug)
            .orElseThrow(() -> new NoSuchElementException("Article not found"));
        return ArticleResponse.from(article, true);
    }

    @Transactional
    public ArticleResponse create(ArticleSaveRequest request) {
        validateRequest(request);
        Article article = new Article();
        applyRequest(article, request);
        String slug = StringUtils.hasText(request.getSlug()) ? normalizeSlug(request.getSlug()) : generateSlug(request.getTitle());
        if (articleRepository.existsBySlugAndDeletedFalse(slug)) {
            slug = uniqueSlug(slug, null);
        }
        article.setSlug(slug);
        Article saved = articleRepository.save(article);
        return ArticleResponse.from(saved, true);
    }

    @Transactional
    public ArticleResponse update(Long id, ArticleSaveRequest request) {
        validateRequest(request);
        Article article = articleRepository.findById(id)
            .filter(item -> !Boolean.TRUE.equals(item.getDeleted()))
            .orElseThrow(() -> new NoSuchElementException("Article not found"));
        applyRequest(article, request);
        String slug = StringUtils.hasText(request.getSlug()) ? normalizeSlug(request.getSlug()) : generateSlug(request.getTitle());
        if (articleRepository.existsBySlugAndIdNotAndDeletedFalse(slug, id)) {
            slug = uniqueSlug(slug, id);
        }
        article.setSlug(slug);
        Article saved = articleRepository.save(article);
        return ArticleResponse.from(saved, true);
    }

    @Transactional
    public void delete(Long id) {
        Article article = articleRepository.findById(id)
            .orElseThrow(() -> new NoSuchElementException("Article not found"));
        article.setDeleted(true);
        articleRepository.save(article);
    }

    @Transactional
    public void incrementViews(String slug) {
        articleRepository.incrementViews(slug);
    }

    public ArticleFiltersResponse getFilters() {
        List<ArticleCategoryResponse> categories = articleCategoryRepository.findByEnabledTrueOrderBySortOrderAscIdAsc()
            .stream()
            .map(ArticleCategoryResponse::from)
            .toList();
        List<TagResponse> tags = tagRepository.findByEnabledTrueOrderBySortOrderAscIdAsc()
            .stream()
            .map(tag -> TagResponse.from(tag, articleTagRepository.countActiveArticlesByTagId(tag.getId())))
            .toList();
        return new ArticleFiltersResponse(categories, tags);
    }

    public List<ArticleCategoryResponse> getCategories() {
        return articleCategoryRepository.findByEnabledTrueOrderBySortOrderAscIdAsc()
            .stream()
            .map(ArticleCategoryResponse::from)
            .toList();
    }

    public List<TagResponse> getTags() {
        return tagRepository.findByEnabledTrueOrderBySortOrderAscIdAsc()
            .stream()
            .map(tag -> TagResponse.from(tag, articleTagRepository.countActiveArticlesByTagId(tag.getId())))
            .toList();
    }

    private void validateRequest(ArticleSaveRequest request) {
        if (request == null || !StringUtils.hasText(request.getTitle())) {
            throw new IllegalArgumentException("Title is required");
        }
        if (!StringUtils.hasText(request.getContent())) {
            throw new IllegalArgumentException("Content is required");
        }
    }

    private void applyRequest(Article article, ArticleSaveRequest request) {
        article.setTitle(request.getTitle().trim());
        article.setTitleEn(trimToNull(request.getTitleEn()));
        article.setContent(request.getContent());
        article.setExcerpt(StringUtils.hasText(request.getExcerpt()) ? request.getExcerpt().trim() : generateExcerpt(request.getContent()));
        article.setExcerptEn(StringUtils.hasText(request.getExcerptEn()) ? request.getExcerptEn().trim() : "");
        article.setAuthor(StringUtils.hasText(request.getAuthor()) ? request.getAuthor().trim() : "Evan");
        article.setStatus(parseStatus(request.getStatus()) != null ? parseStatus(request.getStatus()) : ArticleStatus.PUBLISHED);
        article.setFeatured(Boolean.TRUE.equals(request.getFeatured()));
        article.setCoverImage(trimToNull(request.getCoverImage()));
        article.setReadingTime(calculateReadingTime(request.getContent()));

        if (request.getCategoryId() != null) {
            ArticleCategory category = articleCategoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new NoSuchElementException("Category not found"));
            article.setCategory(category);
        } else {
            article.setCategory(null);
        }

        List<Tag> tags = request.getTagIds() == null || request.getTagIds().isEmpty()
            ? Collections.emptyList()
            : tagRepository.findAllById(request.getTagIds());
        article.replaceTags(tags);
    }

    private ArticleStatus parseStatus(String status) {
        if (!StringUtils.hasText(status)) {
            return null;
        }
        try {
            return ArticleStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String generateExcerpt(String content) {
        String plain = content
            .replaceAll("```[\\s\\S]*?```", " ")
            .replaceAll("[#>*`\\[\\]_-]", " ")
            .replaceAll("\\s+", " ")
            .trim();
        if (plain.length() <= 150) {
            return plain;
        }
        return plain.substring(0, 150) + "...";
    }

    private Integer calculateReadingTime(String content) {
        String plain = content.replaceAll("[#>*`\\[\\]_-]", "");
        return Math.max(1, (int) Math.ceil(plain.length() / 500.0));
    }

    public String generateSlug(String title) {
        String normalized = Normalizer.normalize(title, Normalizer.Form.NFKD)
            .replaceAll("\\p{M}", "")
            .toLowerCase(Locale.ROOT)
            .replaceAll("[^a-z0-9\\u4e00-\\u9fa5]+", "-")
            .replaceAll("^-|-$", "");
        return StringUtils.hasText(normalized) ? normalized : "article";
    }

    private String normalizeSlug(String slug) {
        return slug.toLowerCase(Locale.ROOT)
            .replaceAll("[^a-z0-9\\u4e00-\\u9fa5]+", "-")
            .replaceAll("^-|-$", "");
    }

    private String uniqueSlug(String baseSlug, Long currentId) {
        String candidate = baseSlug;
        int index = 2;
        while (currentId == null
            ? articleRepository.existsBySlugAndDeletedFalse(candidate)
            : articleRepository.existsBySlugAndIdNotAndDeletedFalse(candidate, currentId)) {
            candidate = baseSlug + "-" + index;
            index++;
        }
        return candidate;
    }

    @Transactional
    public void initializeDefaultsIfEmpty() {
        if (articleCategoryRepository.count() == 0) {
            createCategory("前端", "Frontend", 10);
            createCategory("后端", "Backend", 20);
            createCategory("运维", "DevOps", 30);
            createCategory("生活随笔", "Life", 40);
        }
        if (tagRepository.count() == 0) {
            createTag("React", "React", "blue", 10);
            createTag("TypeScript", "TypeScript", "geekblue", 20);
            createTag("JavaScript", "JavaScript", "gold", 30);
            createTag("Go", "Go", "cyan", 40);
            createTag("Docker", "Docker", "blue", 50);
            createTag("Web", "Web", "green", 60);
            createTag("DevOps", "DevOps", "purple", 70);
            createTag("前端", "Frontend", "blue", 80);
            createTag("后端", "Backend", "green", 90);
            createTag("运维", "Operations", "orange", 100);
        }
        if (articleRepository.countByStatusAndDeletedFalse(ArticleStatus.PUBLISHED) == 0) {
            createSeedArticle("React 18 新特性深度解析", "Deep Dive into React 18 New Features", "react-18-new-features",
                "探索 React 18 中的并发渲染、Suspense 增强以及新的 Hook API",
                "# React 18 新特性深度解析\n\nReact 18 是近年来最重要的版本更新，引入了多项革命性的新特性。\n\n## 并发渲染\n\n并发渲染允许 React 同时准备多个版本的 UI。\n\n```tsx\nimport { createRoot } from 'react-dom/client'\n\ncreateRoot(document.getElementById('root')!).render(<App />)\n```\n\n## 自动批处理\n\nReact 18 默认启用自动批处理，减少不必要的重新渲染。",
                "前端", List.of("React", "JavaScript", "前端"), true, 1250L);
            createSeedArticle("Go 语言高性能 Web 服务实战", "Building High-Performance Web Services with Go", "go-high-performance-web",
                "使用 Go 语言构建高性能 Web 服务的最佳实践",
                "# Go 语言高性能 Web 服务实战\n\nGo 语言以其出色的并发性能和简洁的语法成为构建高性能服务的首选。\n\n## Gin 框架入门\n\n```go\npackage main\n\nimport \"github.com/gin-gonic/gin\"\n```\n\n## 性能优化技巧\n\n1. 使用 sync.Pool 复用对象\n2. 合理设置缓冲区大小\n3. 使用 pprof 进行性能分析",
                "后端", List.of("Go", "Web", "后端"), true, 890L);
            createSeedArticle("Docker 容器化部署完全指南", "Complete Guide to Docker Containerization", "docker-complete-guide",
                "从零开始掌握 Docker 容器化部署的各个环节",
                "# Docker 容器化部署完全指南\n\nDocker 已经成为现代应用部署的标准，本指南帮助你从零掌握容器化技术。\n\n## Dockerfile 最佳实践\n\n```dockerfile\nFROM node:18-alpine AS builder\nWORKDIR /app\nCOPY . .\nRUN npm run build\n```\n\n## 安全最佳实践\n\n1. 最小化镜像大小\n2. 不使用 root 用户运行\n3. 定期更新基础镜像",
                "运维", List.of("Docker", "DevOps", "运维"), false, 720L);
            createSeedArticle("TypeScript 高级类型技巧", "Advanced TypeScript Type Techniques", "typescript-advanced-types",
                "深入探索 TypeScript 高级类型系统",
                "# TypeScript 高级类型技巧\n\nTypeScript 的类型系统非常强大，这里介绍一些高级技巧。\n\n## 条件类型\n\n```typescript\ntype IsString<T> = T extends string ? true : false\n```\n\n## 映射类型\n\n掌握这些高级类型技巧，可以让代码更加类型安全。",
                "前端", List.of("TypeScript", "JavaScript", "前端"), false, 560L);
        }
    }

    private void createCategory(String name, String nameEn, Integer sortOrder) {
        ArticleCategory category = new ArticleCategory();
        category.setName(name);
        category.setNameEn(nameEn);
        category.setSortOrder(sortOrder);
        articleCategoryRepository.save(category);
    }

    private void createTag(String name, String nameEn, String color, Integer sortOrder) {
        Tag tag = new Tag();
        tag.setName(name);
        tag.setNameEn(nameEn);
        tag.setColor(color);
        tag.setSortOrder(sortOrder);
        tagRepository.save(tag);
    }

    private void createSeedArticle(String title, String titleEn, String slug, String excerpt, String content,
                                   String categoryName, List<String> tagNames, Boolean featured, Long views) {
        ArticleSaveRequest request = new ArticleSaveRequest();
        request.setTitle(title);
        request.setTitleEn(titleEn);
        request.setSlug(slug);
        request.setExcerpt(excerpt);
        request.setContent(content);
        request.setFeatured(featured);
        request.setStatus(ArticleStatus.PUBLISHED.name());
        articleCategoryRepository.findByName(categoryName).ifPresent(category -> request.setCategoryId(category.getId()));
        List<Long> tagIds = tagNames.stream()
            .map(tagRepository::findByName)
            .flatMap(Optional::stream)
            .map(Tag::getId)
            .collect(Collectors.toList());
        request.setTagIds(tagIds);
        ArticleResponse response = create(request);
        articleRepository.findById(response.getId()).ifPresent(article -> {
            article.setViews(views);
            article.setPublishedAt(LocalDateTime.now().minusDays(Math.max(1, 10 - response.getId().intValue())));
            articleRepository.save(article);
        });
    }
}
