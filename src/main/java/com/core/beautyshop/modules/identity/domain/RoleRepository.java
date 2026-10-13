package com.core.beautyshop.modules.identity.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;

import java.util.Optional;

@Repository
public interface RoleRepository extends JpaRepository<Role, Long> {
    Optional<Role> findByName(String name);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM Role r WHERE r.name = :name AND r.isDeleted = false")
    Optional<Role> findByNameForUpdate(@Param("name") String name);

    @Query("SELECT r FROM Role r WHERE r.name = :roleName")
    Optional<Role> findByRoleName(@Param("roleName") String roleName);
}
