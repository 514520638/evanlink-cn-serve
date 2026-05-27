package com.evanlink.controller;

import com.evanlink.model.UserInfo;
import com.evanlink.service.UserInfoService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Optional;

@RestController
@RequestMapping("/api/user_info")
@CrossOrigin(origins = "*")
public class UserInfoController {

    @Autowired
    private UserInfoService userInfoService;

    @GetMapping
    public ResponseEntity<UserInfoResponse> getUserInfo(HttpServletRequest request) {
        // 获取客户端IP地址
        String ip = getClientIp(request);
        
        // 检查是否为新访客，如果是新访客则visitorNumber+1
        if (userInfoService.isNewVisitor(ip)) {
            userInfoService.incrementVisitorNumber();
        }
        
        // 记录IP到历史列表（移除旧的，添加新的，保持队列先进先出）
        userInfoService.recordClientIp(ip);
        
        Optional<UserInfo> userInfo = userInfoService.getUserInfo();
        return userInfo.map(UserInfoResponse::from)
                        .map(ResponseEntity::ok)
                        .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<UserInfo> saveUserInfo(@RequestBody UserInfo userInfo) {
        UserInfo saved = userInfoService.save(userInfo);
        return ResponseEntity.ok(saved);
    }
    
    /**
     * 获取客户端真实IP地址
     */
    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("HTTP_CLIENT_IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("HTTP_X_FORWARDED_FOR");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        // 如果是多个IP（经过代理），取第一个IP
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }

    public static class UserInfoResponse {
        private Long id;
        private String name;
        private String nameEn;
        private String title;
        private String titleEn;
        private String bio;
        private String bioEn;
        private String avatar;
        private String wechat;
        private String phoneNumber;
        private String email;
        private String github;
        private String gitee;
        private Long visitorNumber;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;

        public static UserInfoResponse from(UserInfo userInfo) {
            UserInfoResponse response = new UserInfoResponse();
            response.setId(userInfo.getId());
            response.setName(userInfo.getName());
            response.setNameEn(userInfo.getNameEn());
            response.setTitle(userInfo.getTitle());
            response.setTitleEn(userInfo.getTitleEn());
            response.setBio(userInfo.getBio());
            response.setBioEn(userInfo.getBioEn());
            response.setAvatar(userInfo.getAvatar());
            response.setWechat(userInfo.getWechat());
            response.setPhoneNumber(userInfo.getPhoneNumber());
            response.setEmail(userInfo.getEmail());
            response.setGithub(userInfo.getGithub());
            response.setGitee(userInfo.getGitee());
            response.setVisitorNumber(userInfo.getVisitorNumber());
            response.setCreatedAt(userInfo.getCreatedAt());
            response.setUpdatedAt(userInfo.getUpdatedAt());
            return response;
        }

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public String getNameEn() { return nameEn; }
        public void setNameEn(String nameEn) { this.nameEn = nameEn; }

        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }

        public String getTitleEn() { return titleEn; }
        public void setTitleEn(String titleEn) { this.titleEn = titleEn; }

        public String getBio() { return bio; }
        public void setBio(String bio) { this.bio = bio; }

        public String getBioEn() { return bioEn; }
        public void setBioEn(String bioEn) { this.bioEn = bioEn; }

        public String getAvatar() { return avatar; }
        public void setAvatar(String avatar) { this.avatar = avatar; }

        public String getWechat() { return wechat; }
        public void setWechat(String wechat) { this.wechat = wechat; }

        public String getPhoneNumber() { return phoneNumber; }
        public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }

        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }

        public String getGithub() { return github; }
        public void setGithub(String github) { this.github = github; }

        public String getGitee() { return gitee; }
        public void setGitee(String gitee) { this.gitee = gitee; }

        public Long getVisitorNumber() { return visitorNumber; }
        public void setVisitorNumber(Long visitorNumber) { this.visitorNumber = visitorNumber; }

        public LocalDateTime getCreatedAt() { return createdAt; }
        public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

        public LocalDateTime getUpdatedAt() { return updatedAt; }
        public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    }
}
