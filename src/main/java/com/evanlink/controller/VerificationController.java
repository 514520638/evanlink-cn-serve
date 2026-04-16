package com.evanlink.controller;

import com.evanlink.model.ResumeRecord;
import com.evanlink.service.ResumeRecordService;
import com.evanlink.service.VerificationService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/resume/verify")
@CrossOrigin(origins = "*")
public class VerificationController {

    @Autowired
    private VerificationService verificationService;
    
    @Autowired
    private ResumeRecordService resumeRecordService;

    @PostMapping
    public ResponseEntity<Map<String, Object>> verify(@RequestBody Map<String, String> request, HttpServletRequest httpRequest) {
        Map<String, Object> response = new HashMap<>();
        
        String phoneNumber = request.get("phone_number");
        String nameApply = request.get("name_apply");
        String password = request.get("password");
        
        // 将请求数据存入数据库（无论成功失败都记录）
        ResumeRecord record = new ResumeRecord();
        record.setPhoneNumber(phoneNumber);
        record.setNameApply(nameApply);
        record.setPassword(password);
        record.setIpAddress(getClientIp(httpRequest));
        record.setTimeApply(LocalDateTime.now());
        resumeRecordService.save(record);
        
        // 验证手机号格式
        if (!verificationService.isValidPhoneNumber(phoneNumber)) {
            response.put("success", false);
            response.put("message", "手机号格式不正确");
            return ResponseEntity.ok(response);
        }
        
        // 验证密码
        if (!verificationService.isValidPassword(password)) {
            response.put("success", false);
            response.put("message", "密码错误");
            return ResponseEntity.ok(response);
        }
        
        // 验证通过，返回简历URL
        response.put("success", true);
        response.put("message", "验证通过");
        response.put("resumeUrl", verificationService.getResumeUrl());
        
        return ResponseEntity.ok(response);
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
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }
}
