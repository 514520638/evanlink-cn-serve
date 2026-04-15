package com.evanlink.controller;

import com.evanlink.model.UserInfo;
import com.evanlink.service.UserInfoService;
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
    public ResponseEntity<UserInfo> getUserInfo() {
        Optional<UserInfo> userInfo = userInfoService.getUserInfo();
        return userInfo.map(ResponseEntity::ok)
                        .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<UserInfo> saveUserInfo(@RequestBody UserInfo userInfo) {
        UserInfo saved = userInfoService.save(userInfo);
        return ResponseEntity.ok(saved);
    }
}
