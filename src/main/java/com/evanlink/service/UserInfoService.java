package com.evanlink.service;

import com.evanlink.model.ClientHistory;
import com.evanlink.model.UserInfo;
import com.evanlink.repository.ClientHistoryRepository;
import com.evanlink.repository.UserInfoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class UserInfoService {

    @Autowired
    private UserInfoRepository userInfoRepository;
    
    @Autowired
    private ClientHistoryRepository clientHistoryRepository;

    public Optional<UserInfo> getUserInfo() {
        return userInfoRepository.findFirstByOrderByIdAsc();
    }

    public UserInfo save(UserInfo userInfo) {
        return userInfoRepository.save(userInfo);
    }
    
    @Transactional
    public UserInfo incrementVisitorNumber() {
        Optional<UserInfo> userInfoOpt = userInfoRepository.findFirstByOrderByIdAsc();
        if (userInfoOpt.isPresent()) {
            UserInfo userInfo = userInfoOpt.get();
            userInfo.setVisitorNumber(userInfo.getVisitorNumber() == null ? 1L : userInfo.getVisitorNumber() + 1);
            return userInfoRepository.save(userInfo);
        }
        return null;
    }
    
    @Transactional
    public void recordClientIp(String ip) {
        // 如果IP已存在，删除旧的
        Optional<ClientHistory> existing = clientHistoryRepository.findByIp(ip);
        if (existing.isPresent()) {
            clientHistoryRepository.delete(existing.get());
        }
        
        // 添加新IP
        ClientHistory clientHistory = new ClientHistory();
        clientHistory.setIp(ip);
        clientHistoryRepository.save(clientHistory);
        
        // 保持队列先进先出，只保留最新N条记录（可以根据需要调整数量）
        List<ClientHistory> allHistory = clientHistoryRepository.findAll();
        if (allHistory.size() > 100) {
            // 删除最早的记录，保留最新的100条
            List<ClientHistory> toDelete = allHistory.subList(0, allHistory.size() - 100);
            clientHistoryRepository.deleteAll(toDelete);
        }
    }
    
    @Transactional
    public boolean isNewVisitor(String ip) {
        return !clientHistoryRepository.existsByIp(ip);
    }

    @Transactional
    public void deleteAll() {
        userInfoRepository.deleteAll();
    }
}
