package com.evanlink.repository;

import com.evanlink.model.ClientHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ClientHistoryRepository extends JpaRepository<ClientHistory, Long> {
    
    Optional<ClientHistory> findByIp(String ip);
    
    boolean existsByIp(String ip);
}
