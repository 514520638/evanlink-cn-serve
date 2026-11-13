package com.evanlink.auth;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;
public interface RevokedAdminSessionRepository extends JpaRepository<RevokedAdminSession,String> {
    @Transactional void deleteByExpiresAtLessThan(long now);
}
