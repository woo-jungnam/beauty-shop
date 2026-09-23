package com.core.beautyshop.modules.identity.domain;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);

    Optional<User> findByEmail(String email);

    Optional<User> findByUsernameOrEmail(String username, String email);

    Boolean existsByUsername(String username);

    Boolean existsByEmail(String email);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT user FROM User user WHERE user.id = :id AND user.isDeleted = false")
    Optional<User> findByIdForUpdate(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT user FROM User user WHERE user.username = :username AND user.isDeleted = false")
    Optional<User> findByUsernameForUpdate(@Param("username") String username);

    @Query("SELECT user.tokenVersion FROM User user WHERE user.id = :id AND user.isDeleted = false")
    Optional<Integer> findTokenVersionById(@Param("id") Long id);
}

