package com.csit314.platform.repository;

import com.csit314.platform.model.Shortlist;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ShortlistRepository extends JpaRepository<Shortlist, Long> {

    Optional<Shortlist> findByCustomerIdAndProjectId(Long customerId, Long projectId);

    boolean existsByCustomerIdAndProjectId(Long customerId, Long projectId);

    List<Shortlist> findByCustomerIdOrderByCreatedAtDesc(Long customerId);

    // IDR-11/12 - across every project the designer owns
    List<Shortlist> findByProjectDesignerIdOrderByCreatedAtDesc(Long designerId);

    // IDR-08
    long countByProjectDesignerIdAndCreatedAtAfter(Long designerId, LocalDateTime since);
}
