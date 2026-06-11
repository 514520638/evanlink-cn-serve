package com.evanlink.controller;

import com.evanlink.model.Skill;
import com.evanlink.model.UserInfo;
import com.evanlink.service.AdminAuthService;
import com.evanlink.service.SkillService;
import com.evanlink.service.UserInfoService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    @Autowired
    private AdminAuthService adminAuthService;

    @Autowired
    private UserInfoService userInfoService;

    @Autowired
    private SkillService skillService;

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request, HttpServletResponse response) {
        Optional<String> token = adminAuthService.login(request.getUsername(), request.getPassword());
        if (token.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Collections.singletonMap("message", "账号或密码错误"));
        }

        response.addHeader("Set-Cookie", buildAdminCookie(token.get(), adminAuthService.getTokenTtlSeconds()).toString());
        return ResponseEntity.ok(Map.of("success", true));
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpServletResponse response) {
        response.addHeader("Set-Cookie", buildAdminCookie("", 0).toString());
        return ResponseEntity.ok(Map.of("success", true));
    }

    @GetMapping("/profile")
    public ResponseEntity<?> getProfile(
            @RequestHeader(name = "Authorization", required = false) String authorization,
            HttpServletRequest request
    ) {
        if (!isAuthorized(authorization, request)) {
            return unauthorized();
        }

        Optional<UserInfo> userInfo = userInfoService.getUserInfo();
        return ResponseEntity.ok(new ProfileResponse(
            userInfo.map(AdminUserInfoResponse::from).orElse(null),
            skillService.getAllSkills()
        ));
    }

    @PutMapping("/profile")
    public ResponseEntity<?> updateProfile(
            @RequestHeader(name = "Authorization", required = false) String authorization,
            HttpServletRequest httpRequest,
            @RequestBody ProfileRequest request
    ) {
        if (!isAuthorized(authorization, httpRequest)) {
            return unauthorized();
        }

        UserInfo userInfo = userInfoService.updatePrimaryUserInfo(request.getUserInfo());
        List<Skill> skills = skillService.replaceAll(request.getSkills());
        return ResponseEntity.ok(new ProfileResponse(AdminUserInfoResponse.from(userInfo), skills));
    }

    private boolean isAuthorized(String authorization, HttpServletRequest request) {
        return adminAuthService.isValidAuthorization(authorization)
            || adminAuthService.isValidToken(getAdminCookieValue(request));
    }

    private String getAdminCookieValue(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (AdminAuthService.ADMIN_COOKIE_NAME.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    private ResponseCookie buildAdminCookie(String token, long maxAgeSeconds) {
        return ResponseCookie.from(AdminAuthService.ADMIN_COOKIE_NAME, token)
            .httpOnly(true)
            .secure(false)
            .sameSite("Lax")
            .path("/")
            .maxAge(maxAgeSeconds)
            .build();
    }

    private ResponseEntity<Map<String, String>> unauthorized() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
            .body(Collections.singletonMap("message", "登录已失效，请重新登录"));
    }

    public static class LoginRequest {
        private String username;
        private String password;

        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }

        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
    }

    public static class ProfileRequest {
        private UserInfo userInfo;
        private List<Skill> skills;

        public UserInfo getUserInfo() { return userInfo; }
        public void setUserInfo(UserInfo userInfo) { this.userInfo = userInfo; }

        public List<Skill> getSkills() { return skills; }
        public void setSkills(List<Skill> skills) { this.skills = skills; }
    }

    public static class ProfileResponse {
        private AdminUserInfoResponse userInfo;
        private List<Skill> skills;

        public ProfileResponse(AdminUserInfoResponse userInfo, List<Skill> skills) {
            this.userInfo = userInfo;
            this.skills = skills;
        }

        public AdminUserInfoResponse getUserInfo() { return userInfo; }
        public void setUserInfo(AdminUserInfoResponse userInfo) { this.userInfo = userInfo; }

        public List<Skill> getSkills() { return skills; }
        public void setSkills(List<Skill> skills) { this.skills = skills; }
    }

    public static class AdminUserInfoResponse {
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
        private String resumeUrl;
        private Long visitorNumber;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;

        public static AdminUserInfoResponse from(UserInfo userInfo) {
            AdminUserInfoResponse response = new AdminUserInfoResponse();
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
            response.setResumeUrl(userInfo.getResumeUrl());
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
        public String getResumeUrl() { return resumeUrl; }
        public void setResumeUrl(String resumeUrl) { this.resumeUrl = resumeUrl; }
        public Long getVisitorNumber() { return visitorNumber; }
        public void setVisitorNumber(Long visitorNumber) { this.visitorNumber = visitorNumber; }
        public LocalDateTime getCreatedAt() { return createdAt; }
        public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
        public LocalDateTime getUpdatedAt() { return updatedAt; }
        public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    }
}
