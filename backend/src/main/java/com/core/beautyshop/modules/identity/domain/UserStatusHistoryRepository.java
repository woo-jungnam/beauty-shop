package com.core.beautyshop.modules.identity.domain;
import org.springframework.data.jpa.repository.JpaRepository;
public interface UserStatusHistoryRepository extends JpaRepository<UserStatusHistory, Long> {
    org.springframework.data.domain.Page<UserStatusHistory> findByUserIdOrderByCreatedAtDesc(Long userId, org.springframework.data.domain.Pageable pageable);
}
