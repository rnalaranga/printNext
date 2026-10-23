package com.rr.secureprint.repository;

import com.rr.secureprint.entity.SystemSettingsEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SystemSettingsRepository extends JpaRepository<SystemSettingsEntity, Long> {
}
