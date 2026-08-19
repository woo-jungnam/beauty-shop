package com.core.beautyshop.modules.spa.domain;

import com.core.beautyshop.modules.spa.domain.UserServiceTicket;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UserServiceTicketRepository extends JpaRepository<UserServiceTicket, Long> {
    List<UserServiceTicket> findByUserId(Long userId);
}
