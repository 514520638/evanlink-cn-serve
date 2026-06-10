package com.evanlink.dto;

import com.evanlink.model.Tag;

public class TagResponse {
    private Long id;
    private String name;
    private String nameEn;
    private String color;
    private Long articleCount;

    public static TagResponse from(Tag tag, Long articleCount) {
        TagResponse response = new TagResponse();
        response.setId(tag.getId());
        response.setName(tag.getName());
        response.setNameEn(tag.getNameEn());
        response.setColor(tag.getColor());
        response.setArticleCount(articleCount);
        return response;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getNameEn() { return nameEn; }
    public void setNameEn(String nameEn) { this.nameEn = nameEn; }

    public String getColor() { return color; }
    public void setColor(String color) { this.color = color; }

    public Long getArticleCount() { return articleCount; }
    public void setArticleCount(Long articleCount) { this.articleCount = articleCount; }
}
