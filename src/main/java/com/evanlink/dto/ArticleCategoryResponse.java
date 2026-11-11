package com.evanlink.dto;

import com.evanlink.model.ArticleCategory;

public class ArticleCategoryResponse {
    private Long id;
    private String name;
    private String nameEn;
    private Integer sortOrder;

    public static ArticleCategoryResponse from(ArticleCategory category) {
        ArticleCategoryResponse response = new ArticleCategoryResponse();
        response.setId(category.getId());
        response.setName(category.getName());
        response.setNameEn(category.getNameEn());
        response.setSortOrder(category.getSortOrder());
        return response;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getNameEn() { return nameEn; }
    public void setNameEn(String nameEn) { this.nameEn = nameEn; }

    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
}
