package com.rr.secureprint.repository;

import com.rr.secureprint.entity.ScanJobEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ScanJobRepository extends JpaRepository<ScanJobEntity, Long> {

    List<ScanJobEntity> findTop50ByOrderByCompletedAtDesc();

    List<ScanJobEntity> findByUsernameOrderByCompletedAtDesc(String username);

    long countByUsername(String username);

    boolean existsByPrinterIpAndFileName(String printerIp, String fileName);

    Page<ScanJobEntity> findAllByOrderByCompletedAtDesc(Pageable pageable);
}
