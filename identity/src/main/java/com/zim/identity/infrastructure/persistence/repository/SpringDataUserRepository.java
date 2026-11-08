package com.zim.identity.infrastructure.persistence.repository;

import com.zim.identity.infrastructure.persistence.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface SpringDataUserRepository
    extends JpaRepository<UserEntity, UUID> {

  /**
   * The unique index is on {@code lower(email)}, so the comparison is on
   * {@code lower(e.email)} to be served by it (login is unauthenticated: a
   * sequential scan there would be a cheap way to load the database). The
   * parameter is already normalized (see {@code Email}).
   */
  @Query("select count(e) > 0 from UserEntity e where lower(e.email) = :email")
  boolean existsByEmail(@Param("email") String email);

  boolean existsByTenantIdAndOwnerTrue(UUID tenantId);

  @Query("select e from UserEntity e where lower(e.email) = :email")
  Optional<UserEntity> findByEmail(@Param("email") String email);
}
