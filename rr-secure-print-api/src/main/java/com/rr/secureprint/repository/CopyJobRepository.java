package com.rr.secureprint.repository;

import com.rr.secureprint.entity.CopyJobEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CopyJobRepository extends JpaRepository<CopyJobEntity, Long> {

    boolean existsByPrinterIpAndPrinterJobId(String printerIp, String printerJobId);

    List<CopyJobEntity> findTop50ByOrderByCompletedAtDesc();

    List<CopyJobEntity> findByUsernameOrderByCompletedAtDesc(String username);

    @Query("SELECT COALESCE(SUM(c.pages), 0) FROM CopyJobEntity c WHERE LOWER(c.username) = LOWER(:username)")
    long sumPagesByUsername(@Param("username") String username);

    @Query("SELECT COALESCE(SUM(c.pages), 0) FROM CopyJobEntity c")
    long sumTotalPages();
}
