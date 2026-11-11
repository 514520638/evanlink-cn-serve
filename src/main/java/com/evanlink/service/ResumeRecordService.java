package com.evanlink.service;

import com.evanlink.model.ResumeRecord;
import com.evanlink.repository.ResumeRecordRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ResumeRecordService {

    private static final int MAX_RECORDS = 1000;
    
    @Autowired
    private ResumeRecordRepository resumeRecordRepository;

    @Transactional
    public ResumeRecord save(ResumeRecord record) {
        // 保存新记录
        ResumeRecord saved = resumeRecordRepository.save(record);
        
        // 检查记录数量，超过1000条则删除最早的记录
        List<ResumeRecord> allRecords = resumeRecordRepository.findAll();
        if (allRecords.size() > MAX_RECORDS) {
            // 删除最早的记录，保留最新的MAX_RECORDS条
            List<ResumeRecord> toDelete = allRecords.subList(0, allRecords.size() - MAX_RECORDS);
            resumeRecordRepository.deleteAll(toDelete);
        }
        
        return saved;
    }

    public List<ResumeRecord> getAllRecords() {
        return resumeRecordRepository.findAll();
    }

    @Transactional
    public void deleteById(Long id) {
        resumeRecordRepository.deleteById(id);
    }
}
