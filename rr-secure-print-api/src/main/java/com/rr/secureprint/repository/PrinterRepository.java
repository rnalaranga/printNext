package com.rr.secureprint.repository;

import com.rr.secureprint.entity.PrinterEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PrinterRepository extends JpaRepository<PrinterEntity, Long> {
    Optional<PrinterEntity> findByPrinterCode(String printerCode);
    List<PrinterEntity> findByIsActiveTrue();
}
