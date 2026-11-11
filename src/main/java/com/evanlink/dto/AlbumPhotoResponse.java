package com.evanlink.dto;

import com.evanlink.model.AlbumPhoto;
import java.time.LocalDateTime;

public class AlbumPhotoResponse {
    private Long id;
    private String url;
    private String originalName;
    private String contentType;
    private String mediaType;
    private Long fileSize;
    private String title;
    private String description;
    private Integer sortOrder;
    private LocalDateTime createdAt;

    public static AlbumPhotoResponse from(AlbumPhoto photo) {
        AlbumPhotoResponse response = new AlbumPhotoResponse();
        response.setId(photo.getId());
        response.setUrl(photo.getUrl());
        response.setOriginalName(photo.getOriginalName());
        response.setContentType(photo.getContentType());
        response.setMediaType(photo.getContentType() != null && photo.getContentType().startsWith("video/") ? "video" : "image");
        response.setFileSize(photo.getFileSize());
        response.setTitle(photo.getTitle());
        response.setDescription(photo.getDescription());
        response.setSortOrder(photo.getSortOrder());
        response.setCreatedAt(photo.getCreatedAt());
        return response;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }

    public String getOriginalName() { return originalName; }
    public void setOriginalName(String originalName) { this.originalName = originalName; }

    public String getContentType() { return contentType; }
    public void setContentType(String contentType) { this.contentType = contentType; }

    public String getMediaType() { return mediaType; }
    public void setMediaType(String mediaType) { this.mediaType = mediaType; }

    public Long getFileSize() { return fileSize; }
    public void setFileSize(Long fileSize) { this.fileSize = fileSize; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
