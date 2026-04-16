package com.evanlink.controller;

import com.evanlink.model.UserInfo;
import com.evanlink.service.UserInfoService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@RestController
@RequestMapping("/api/user_info")
@CrossOrigin(origins = "*")
public class UserInfoController {

    @Autowired
    private UserInfoService userInfoService;

    @GetMapping
    public ResponseEntity<UserInfo> getUserInfo(HttpServletRequest request) {
        // 获取客户端IP地址
        String ip = getClientIp(request);
        
        // 检查是否为新访客，如果是新访客则visitorNumber+1
        if (userInfoService.isNewVisitor(ip)) {
            userInfoService.incrementVisitorNumber();
        }
        
        // 记录IP到历史列表（移除旧的，添加新的，保持队列先进先出）
        userInfoService.recordClientIp(ip);
        
        Optional<UserInfo> userInfo = userInfoService.getUserInfo();
        return userInfo.map(ResponseEntity::ok)
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
}
