package com.evanlink.repository;

import com.evanlink.model.ResumeRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ResumeRecordRepository extends JpaRepository<ResumeRecord, Long> {
}
