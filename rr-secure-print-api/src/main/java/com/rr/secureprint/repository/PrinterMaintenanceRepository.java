package com.rr.secureprint.repository;

import com.rr.secureprint.entity.PrinterMaintenanceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PrinterMaintenanceRepository extends JpaRepository<PrinterMaintenanceEntity, Long> {
    List<PrinterMaintenanceEntity> findAllByOrderByServiceDateDescIdDesc();
}
