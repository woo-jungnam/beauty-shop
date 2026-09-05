package com.core.beautyshop.modules.spa.domain;

import com.core.beautyshop.modules.spa.domain.enums.TicketStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserServiceTicketRepository extends JpaRepository<UserServiceTicket, Long> {

    @EntityGraph(attributePaths = {"servicePackage", "servicePackage.items", "servicePackage.items.service"})
    List<UserServiceTicket> findByUserId(Long userId);

    @EntityGraph(attributePaths = {"servicePackage", "servicePackage.items", "servicePackage.items.service"})
    List<UserServiceTicket> findByUserIdOrderByCreatedAtDesc(Long userId);

    @EntityGraph(attributePaths = {"servicePackage", "servicePackage.items", "servicePackage.items.service"})
    List<UserServiceTicket> findByUserIdAndStatusOrderByCreatedAtDesc(Long userId, TicketStatus status);

    @EntityGraph(attributePaths = {"servicePackage", "servicePackage.items", "servicePackage.items.service"})
    Optional<UserServiceTicket> findByIdAndUserId(Long id, Long userId);
}
