package com.evanlink.controller;

import com.evanlink.dto.AlbumPhotoResponse;
import com.evanlink.service.AdminAuthService;
import com.evanlink.service.AlbumPhotoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/api/album/photos")
@CrossOrigin(origins = "*")
public class AlbumPhotoController {

    private static final Logger logger = LoggerFactory.getLogger(AlbumPhotoController.class);

    private final AlbumPhotoService albumPhotoService;
    private final AdminAuthService adminAuthService;

    public AlbumPhotoController(AlbumPhotoService albumPhotoService, AdminAuthService adminAuthService) {
        this.albumPhotoService = albumPhotoService;
        this.adminAuthService = adminAuthService;
    }

    @GetMapping
    public ResponseEntity<List<AlbumPhotoResponse>> listPhotos() {
        return ResponseEntity.ok(albumPhotoService.list());
    }

    @PostMapping
    public ResponseEntity<?> uploadPhoto(
            @RequestHeader(name = "Authorization", required = false) String authorization,
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) String title,
            @RequestParam(required = false) String description
    ) {
        if (!adminAuthService.isValidAuthorization(authorization)) {
            return unauthorized();
        }
        try {
            return ResponseEntity.ok(albumPhotoService.upload(file, title, description));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Collections.singletonMap("message", ex.getMessage()));
        } catch (Exception ex) {
            logger.error("Upload album photo failed", ex);
            return ResponseEntity.internalServerError().body(Collections.singletonMap("message", "上传照片失败"));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deletePhoto(
            @RequestHeader(name = "Authorization", required = false) String authorization,
            @PathVariable Long id
    ) {
        if (!adminAuthService.isValidAuthorization(authorization)) {
            return unauthorized();
        }
        try {
            albumPhotoService.delete(id);
            return ResponseEntity.noContent().build();
        } catch (NoSuchElementException ex) {
            return ResponseEntity.notFound().build();
        }
    }

    private ResponseEntity<Map<String, String>> unauthorized() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
            .body(Collections.singletonMap("message", "请先登录后再管理相册"));
    }
}
