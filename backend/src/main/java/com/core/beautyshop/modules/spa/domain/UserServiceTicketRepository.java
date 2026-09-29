package com.core.beautyshop.modules.spa.domain;

import com.core.beautyshop.modules.spa.domain.enums.TicketStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.*;

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

    boolean existsByOrderId(Long orderId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from UserServiceTicket t where t.orderId = :orderId")
    Optional<UserServiceTicket> findByOrderIdForUpdate(Long orderId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT ticket FROM UserServiceTicket ticket WHERE ticket.id = :id")
    Optional<UserServiceTicket> findByIdForUpdate(@Param("id") Long id);

    @EntityGraph(attributePaths = {"servicePackage"})
    Page<UserServiceTicket> findByIsDeletedFalse(Pageable pageable);

    @EntityGraph(attributePaths = {"servicePackage"})
    Page<UserServiceTicket> findByUserIdAndIsDeletedFalse(Long userId, Pageable pageable);

    @EntityGraph(attributePaths = {"servicePackage"})
    Page<UserServiceTicket> findByStatusAndIsDeletedFalse(TicketStatus status, Pageable pageable);
}
