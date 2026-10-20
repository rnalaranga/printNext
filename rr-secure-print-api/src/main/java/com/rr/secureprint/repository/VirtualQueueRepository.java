package com.rr.secureprint.repository;

import com.rr.secureprint.entity.VirtualQueueEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface VirtualQueueRepository extends JpaRepository<VirtualQueueEntity, Long> {
    Optional<VirtualQueueEntity> findByQueueCode(String queueCode);
    Optional<VirtualQueueEntity> findByWindowsPrinterName(String windowsPrinterName);
    Optional<VirtualQueueEntity> findByIsDefaultTrue();
    List<VirtualQueueEntity> findAllByOrderByIsDefaultDescIdAsc();
    List<VirtualQueueEntity> findByIsActiveTrue();
}
