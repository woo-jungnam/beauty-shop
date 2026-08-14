package com.core.beautyshop.repository;

import com.core.beautyshop.entities.booking.UserServiceTicket;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UserServiceTicketRepository extends JpaRepository<UserServiceTicket, Long> {
    List<UserServiceTicket> findByUserId(Long userId);
}
