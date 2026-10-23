package com.rr.secureprint.repository;

import com.rr.secureprint.entity.DirectoryConfigEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DirectoryConfigRepository extends JpaRepository<DirectoryConfigEntity, Long> {
}
