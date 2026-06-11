package com.evanlink.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.nio.file.Path;

@Service
public class AlbumMediaMirrorService {

    private static final Logger logger = LoggerFactory.getLogger(AlbumMediaMirrorService.class);
    private static final String MIRROR_SECRET_HEADER = "X-Upload-Mirror-Secret";

    @Value("${app.upload.mirror.url:}")
    private String mirrorUrl;

    @Value("${app.upload.mirror.delete-url:}")
    private String mirrorDeleteUrl;

    @Value("${app.upload.mirror.secret:}")
    private String mirrorSecret;

    private final RestTemplate restTemplate = new RestTemplate();

    public void mirrorUpload(Path filePath, String fileName) {
        if (!isConfigured(mirrorUrl)) {
            return;
        }

        try {
            HttpHeaders headers = buildHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("fileName", fileName);
            body.add("file", new FileSystemResource(filePath.toFile()) {
                @Override
                public String getFilename() {
                    return fileName;
                }
            });

            restTemplate.postForEntity(mirrorUrl, new HttpEntity<>(body, headers), String.class);
        } catch (Exception ex) {
            logger.warn("Mirror album media upload failed, fileName={}", fileName, ex);
        }
    }

    public void mirrorDelete(String fileName) {
        if (!isConfigured(mirrorDeleteUrl)) {
            return;
        }

        try {
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("fileName", fileName);
            restTemplate.postForEntity(mirrorDeleteUrl, new HttpEntity<>(body, buildHeaders()), String.class);
        } catch (Exception ex) {
            logger.warn("Mirror album media delete failed, fileName={}", fileName, ex);
        }
    }

    private boolean isConfigured(String url) {
        return url != null && !url.isBlank() && mirrorSecret != null && !mirrorSecret.isBlank();
    }

    private HttpHeaders buildHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(MIRROR_SECRET_HEADER, mirrorSecret);
        return headers;
    }
}
