package com.evanlink.dto;

import java.util.List;

public class ArticleFiltersResponse {
    private List<ArticleCategoryResponse> categories;
    private List<TagResponse> tags;

    public ArticleFiltersResponse(List<ArticleCategoryResponse> categories, List<TagResponse> tags) {
        this.categories = categories;
        this.tags = tags;
    }

    public List<ArticleCategoryResponse> getCategories() { return categories; }
    public void setCategories(List<ArticleCategoryResponse> categories) { this.categories = categories; }

    public List<TagResponse> getTags() { return tags; }
    public void setTags(List<TagResponse> tags) { this.tags = tags; }
}
