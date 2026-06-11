package com.evanlink.service;

import com.evanlink.dto.AlbumPhotoResponse;
import com.evanlink.model.AlbumPhoto;
import com.evanlink.repository.AlbumPhotoRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;

@Service
public class AlbumPhotoService {

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
        "image/jpeg",
        "image/png",
        "image/webp",
        "image/gif",
        "video/mp4",
        "video/webm",
        "video/quicktime"
    );

    @Value("${app.upload.dir:uploads}")
    private String uploadDir;

    private final AlbumPhotoRepository albumPhotoRepository;

    public AlbumPhotoService(AlbumPhotoRepository albumPhotoRepository) {
        this.albumPhotoRepository = albumPhotoRepository;
    }

    public List<AlbumPhotoResponse> list() {
        return albumPhotoRepository.findByDeletedFalseOrderBySortOrderAscCreatedAtDesc()
            .stream()
            .map(AlbumPhotoResponse::from)
            .toList();
    }

    @Transactional
    public List<AlbumPhotoResponse> upload(List<MultipartFile> files, String title, String description) {
        if (files == null || files.isEmpty()) {
            throw new IllegalArgumentException("请选择要上传的照片或视频");
        }
        return files.stream()
            .map(file -> uploadOne(file, title, description))
            .toList();
    }

    private AlbumPhotoResponse uploadOne(MultipartFile file, String title, String description) {
        validate(file);

        String originalName = StringUtils.cleanPath(file.getOriginalFilename() == null ? "photo" : file.getOriginalFilename());
        String extension = getExtension(originalName);
        String fileName = UUID.randomUUID() + extension;
        Path albumDir = Paths.get(uploadDir).toAbsolutePath().normalize().resolve("album");
        Path target = albumDir.resolve(fileName);

        try {
            Files.createDirectories(albumDir);
            file.transferTo(target);
        } catch (IOException ex) {
            throw new IllegalStateException("保存媒体失败", ex);
        }

        AlbumPhoto photo = new AlbumPhoto();
        photo.setUrl("/uploads/album/" + fileName);
        photo.setFileName(fileName);
        photo.setOriginalName(originalName);
        photo.setContentType(file.getContentType());
        photo.setFileSize(file.getSize());
        photo.setTitle(trimToNull(title));
        photo.setDescription(trimToNull(description));

        return AlbumPhotoResponse.from(albumPhotoRepository.save(photo));
    }

    @Transactional
    public void delete(Long id) {
        AlbumPhoto photo = albumPhotoRepository.findByIdAndDeletedFalse(id)
            .orElseThrow(NoSuchElementException::new);
        photo.setDeleted(true);
        albumPhotoRepository.save(photo);

        if (photo.getFileName() == null || photo.getFileName().isBlank()) {
            return;
        }

        Path filePath = Paths.get(uploadDir).toAbsolutePath().normalize().resolve("album").resolve(photo.getFileName());
        try {
            Files.deleteIfExists(filePath);
        } catch (IOException ignored) {
            // The database state is the source of truth; a missing local file should not block deletion.
        }
    }

    private void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("请选择要上传的照片或视频");
        }
        if (!ALLOWED_CONTENT_TYPES.contains(file.getContentType())) {
            throw new IllegalArgumentException("仅支持 JPG、PNG、WebP、GIF 图片和 MP4、WebM、MOV 视频");
        }
    }

    private String getExtension(String fileName) {
        int index = fileName.lastIndexOf('.');
        if (index < 0) {
            return ".jpg";
        }
        return fileName.substring(index).toLowerCase(Locale.ROOT);
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
