package com.csit314.platform.repository;

import com.csit314.platform.model.SystemErrorLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SystemErrorLogRepository extends JpaRepository<SystemErrorLog, Long> {

    // PM-15 - most recent first, capped so one bad afternoon doesn't turn this into an
    // unbounded table scan on every page load
    List<SystemErrorLog> findTop50ByOrderByOccurredAtDesc();
}
