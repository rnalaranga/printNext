package com.rr.secureprint.repository;

import com.rr.secureprint.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<UserEntity, Long> {
    Optional<UserEntity> findByUsername(String username);
    Optional<UserEntity> findByUsernameIgnoreCase(String username);
    Optional<UserEntity> findByUserSid(String userSid);
    Optional<UserEntity> findByBadgeId(String badgeId);
    Optional<UserEntity> findBySecondaryBadgeId(String secondaryBadgeId);

    @Query("SELECT u FROM UserEntity u WHERE u.badgeId = :code OR u.secondaryBadgeId = :code")
    Optional<UserEntity> findByAnyBadgeId(@Param("code") String code);

    List<UserEntity> findByPinHash(String pinHash);
}
