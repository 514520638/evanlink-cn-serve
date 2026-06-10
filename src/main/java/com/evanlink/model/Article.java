package com.evanlink.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Entity
@Table(name = "article", indexes = {
    @Index(name = "idx_article_slug", columnList = "slug"),
    @Index(name = "idx_article_status_time", columnList = "status,published_at"),
    @Index(name = "idx_article_category", columnList = "category_id")
})
public class Article {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 160)
    private String slug;

    @Column(nullable = false)
    private String title;

    @Column(name = "title_en")
    private String titleEn;

    @Column(length = 500)
    private String excerpt;

    @Column(name = "excerpt_en", length = 500)
    private String excerptEn;

    @Column(nullable = false, columnDefinition = "LONGTEXT")
    private String content;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private ArticleCategory category;

    private String author = "Evan";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ArticleStatus status = ArticleStatus.PUBLISHED;

    private Boolean featured = false;

    @Column(name = "cover_image")
    private String coverImage;

    @Column(name = "reading_time")
    private Integer readingTime = 1;

    private Long views = 0L;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    private Boolean deleted = false;

    @OneToMany(mappedBy = "article", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ArticleTag> articleTags = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
        if (publishedAt == null && status == ArticleStatus.PUBLISHED) {
            publishedAt = now;
        }
        if (views == null) {
            views = 0L;
        }
        if (deleted == null) {
            deleted = false;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
        if (publishedAt == null && status == ArticleStatus.PUBLISHED) {
            publishedAt = updatedAt;
        }
    }

    public void replaceTags(List<Tag> tags) {
        if (tags == null) {
            articleTags.clear();
            return;
        }

        Set<Long> requestedTagIds = tags.stream()
            .map(Tag::getId)
            .collect(Collectors.toSet());

        articleTags.removeIf(articleTag -> !requestedTagIds.contains(articleTag.getTag().getId()));

        Set<Long> existingTagIds = articleTags.stream()
            .map(ArticleTag::getTag)
            .map(Tag::getId)
            .collect(Collectors.toSet());

        for (Tag tag : tags) {
            if (existingTagIds.contains(tag.getId())) {
                continue;
            }
            ArticleTag articleTag = new ArticleTag();
            articleTag.setArticle(this);
            articleTag.setTag(tag);
            articleTags.add(articleTag);
        }
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getSlug() { return slug; }
    public void setSlug(String slug) { this.slug = slug; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getTitleEn() { return titleEn; }
    public void setTitleEn(String titleEn) { this.titleEn = titleEn; }

    public String getExcerpt() { return excerpt; }
    public void setExcerpt(String excerpt) { this.excerpt = excerpt; }

    public String getExcerptEn() { return excerptEn; }
    public void setExcerptEn(String excerptEn) { this.excerptEn = excerptEn; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public ArticleCategory getCategory() { return category; }
    public void setCategory(ArticleCategory category) { this.category = category; }

    public String getAuthor() { return author; }
    public void setAuthor(String author) { this.author = author; }

    public ArticleStatus getStatus() { return status; }
    public void setStatus(ArticleStatus status) { this.status = status; }

    public Boolean getFeatured() { return featured; }
    public void setFeatured(Boolean featured) { this.featured = featured; }

    public String getCoverImage() { return coverImage; }
    public void setCoverImage(String coverImage) { this.coverImage = coverImage; }

    public Integer getReadingTime() { return readingTime; }
    public void setReadingTime(Integer readingTime) { this.readingTime = readingTime; }

    public Long getViews() { return views; }
    public void setViews(Long views) { this.views = views; }

    public LocalDateTime getPublishedAt() { return publishedAt; }
    public void setPublishedAt(LocalDateTime publishedAt) { this.publishedAt = publishedAt; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public Boolean getDeleted() { return deleted; }
    public void setDeleted(Boolean deleted) { this.deleted = deleted; }

    public List<ArticleTag> getArticleTags() { return articleTags; }
    public void setArticleTags(List<ArticleTag> articleTags) { this.articleTags = articleTags; }
}
