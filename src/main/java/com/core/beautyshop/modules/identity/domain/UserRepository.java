package com.core.beautyshop.modules.identity.domain;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long>, org.springframework.data.jpa.repository.JpaSpecificationExecutor<User> {
    Optional<User> findByUsername(String username);

    Optional<User> findByEmail(String email);

    java.util.List<User> findByPhoneIn(java.util.Collection<String> phones);

    Optional<User> findByUsernameOrEmail(String username, String email);

    Boolean existsByUsername(String username);

    Boolean existsByEmail(String email);

    @Query("SELECT CASE WHEN COUNT(u) > 0 THEN true ELSE false END FROM User u WHERE u.phone = :phone AND u.isDeleted = false")
    boolean existsByPhoneAndNotDeleted(@Param("phone") String phone);

    @Query("SELECT CASE WHEN COUNT(u) > 0 THEN true ELSE false END FROM User u WHERE u.phone = :phone AND u.isDeleted = false AND u.id <> :excludeId")
    boolean existsByPhoneAndNotDeletedExcluding(@Param("phone") String phone, @Param("excludeId") Long excludeId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT user FROM User user WHERE user.id = :id AND user.isDeleted = false")
    Optional<User> findByIdForUpdate(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT user FROM User user WHERE user.username = :username AND user.isDeleted = false")
    Optional<User> findByUsernameForUpdate(@Param("username") String username);

    @Query("SELECT user.tokenVersion FROM User user WHERE user.id = :id AND user.isDeleted = false")
    Optional<Integer> findTokenVersionById(@Param("id") Long id);

    @Query("SELECT COUNT(DISTINCT u.id) FROM User u JOIN u.roles r WHERE u.isDeleted = false " +
            "AND u.status = com.core.beautyshop.modules.identity.domain.enums.AccountStatus.ACTIVE " +
            "AND r.name = 'ROLE_ADMIN' AND r.isDeleted = false AND u.id <> :excludedId")
    long countActiveAdminsExcluding(@Param("excludedId") Long excludedId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM User u JOIN u.roles r WHERE r.id = :roleId AND u.isDeleted = false ORDER BY u.id")
    java.util.List<User> findByRoleIdForUpdate(@Param("roleId") Long roleId);
}

