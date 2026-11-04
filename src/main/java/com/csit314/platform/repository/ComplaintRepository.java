package com.csit314.platform.repository;

import com.csit314.platform.model.Complaint;
import com.csit314.platform.model.ComplaintStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface ComplaintRepository extends JpaRepository<Complaint, Long> {

    List<Complaint> findByDesignerIdOrderByCreatedAtDesc(Long designerId);

    List<Complaint> findAllByOrderByCreatedAtDesc();

    long countByDesignerId(Long designerId);

    // PM-06/PM-08/PM-12 - new complaints within an arbitrary (half-open) window, for
    // both the calendar-period and scheduled single-day reports
    long countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(LocalDateTime start, LocalDateTime end);

    // ADM-10 - at most one open complaint per customer per designer at a time
    boolean existsByCustomerIdAndDesignerIdAndStatus(Long customerId, Long designerId, ComplaintStatus status);
}
