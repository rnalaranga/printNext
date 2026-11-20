package com.rr.secureprint.repository;

import com.rr.secureprint.entity.CounterResetLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CounterResetLogRepository extends JpaRepository<CounterResetLogEntity, Long> {
    List<CounterResetLogEntity> findAllByOrderByResetAtDescIdDesc();
}
