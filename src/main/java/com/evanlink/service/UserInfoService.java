package com.evanlink.service;

import com.evanlink.model.UserInfo;
import com.evanlink.repository.UserInfoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class UserInfoService {

    @Autowired
    private UserInfoRepository userInfoRepository;

    public Optional<UserInfo> getUserInfo() {
        return userInfoRepository.findFirstByOrderByIdAsc();
    }

    public UserInfo save(UserInfo userInfo) {
        return userInfoRepository.save(userInfo);
    }

    @Transactional
    public void deleteAll() {
        userInfoRepository.deleteAll();
    }
}
