package com.evanlink.service;

import com.evanlink.repository.UserInfoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class VerificationService {

    private static final String CORRECT_PASSWORD = "root";

    @Autowired
    private UserInfoRepository userInfoRepository;

    /**
     * 验证手机号格式（中国大陆手机号）
     */
    public boolean isValidPhoneNumber(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.isEmpty()) {
            return false;
        }
        // 中国大陆手机号正则：1开头，第二位是3-9，共11位数字
        String regex = "^1[3-9]\\d{9}$";
        return phoneNumber.matches(regex);
    }

    /**
     * 验证密码
     */
    public boolean isValidPassword(String password) {
        return password != null && password.equals(CORRECT_PASSWORD);
    }

    /**
     * 获取简历URL
     */
    public String getResumeUrl() {
        return userInfoRepository.findFirstByOrderByIdAsc()
            .map(userInfo -> userInfo.getResumeUrl())
            .orElse(null);
    }
}
