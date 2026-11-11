package com.evanlink.dto;

import com.evanlink.model.Article;
import com.evanlink.model.ArticleTag;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class ArticleResponse {
    private Long id;
    private String slug;
    private String title;
    private String titleEn;
    private String excerpt;
    private String excerptEn;
    private String content;
    private Long categoryId;
    private String category;
    private String categoryEn;
    private List<Long> tagIds;
    private List<String> tags;
    private String author;
    private String status;
    private String publishDate;
    private String updateDate;
    private Integer readingTime;
    private Long views;
    private Boolean featured;
    private String coverImage;

    public static ArticleResponse from(Article article, boolean includeContent) {
        ArticleResponse response = new ArticleResponse();
        DateTimeFormatter formatter = DateTimeFormatter.ISO_LOCAL_DATE;
        response.setId(article.getId());
        response.setSlug(article.getSlug());
        response.setTitle(article.getTitle());
        response.setTitleEn(article.getTitleEn());
        response.setExcerpt(article.getExcerpt());
        response.setExcerptEn(article.getExcerptEn());
        if (includeContent) {
            response.setContent(article.getContent());
        }
        if (article.getCategory() != null) {
            response.setCategoryId(article.getCategory().getId());
            response.setCategory(article.getCategory().getName());
            response.setCategoryEn(article.getCategory().getNameEn());
        }
        response.setTagIds(article.getArticleTags().stream()
            .map(ArticleTag::getTag)
            .map(tag -> tag.getId())
            .toList());
        response.setTags(article.getArticleTags().stream()
            .map(ArticleTag::getTag)
            .map(tag -> tag.getName())
            .toList());
        response.setAuthor(article.getAuthor());
        response.setStatus(article.getStatus().name());
        response.setPublishDate(article.getPublishedAt() != null ? article.getPublishedAt().format(formatter) : null);
        response.setUpdateDate(article.getUpdatedAt() != null ? article.getUpdatedAt().format(formatter) : null);
        response.setReadingTime(article.getReadingTime());
        response.setViews(article.getViews());
        response.setFeatured(article.getFeatured());
        response.setCoverImage(article.getCoverImage());
        return response;
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

    public Long getCategoryId() { return categoryId; }
    public void setCategoryId(Long categoryId) { this.categoryId = categoryId; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getCategoryEn() { return categoryEn; }
    public void setCategoryEn(String categoryEn) { this.categoryEn = categoryEn; }

    public List<Long> getTagIds() { return tagIds; }
    public void setTagIds(List<Long> tagIds) { this.tagIds = tagIds; }

    public List<String> getTags() { return tags; }
    public void setTags(List<String> tags) { this.tags = tags; }

    public String getAuthor() { return author; }
    public void setAuthor(String author) { this.author = author; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getPublishDate() { return publishDate; }
    public void setPublishDate(String publishDate) { this.publishDate = publishDate; }

    public String getUpdateDate() { return updateDate; }
    public void setUpdateDate(String updateDate) { this.updateDate = updateDate; }

    public Integer getReadingTime() { return readingTime; }
    public void setReadingTime(Integer readingTime) { this.readingTime = readingTime; }

    public Long getViews() { return views; }
    public void setViews(Long views) { this.views = views; }

    public Boolean getFeatured() { return featured; }
    public void setFeatured(Boolean featured) { this.featured = featured; }

    public String getCoverImage() { return coverImage; }
    public void setCoverImage(String coverImage) { this.coverImage = coverImage; }
}
