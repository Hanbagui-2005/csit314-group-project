package com.csit314.platform.repository;

import com.csit314.platform.model.Booking;
import com.csit314.platform.model.BookingStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    List<Booking> findByDesignerIdOrderByCreatedAtDesc(Long designerId);

    // PM-06/PM-08 - activity reports
    long countByCreatedAtAfter(LocalDateTime since);

    List<Booking> findByCustomerIdOrderByCreatedAtDesc(Long customerId);

    // CUS-13 - the closest thing this app has to "actually worked with this designer"
    boolean existsByCustomerIdAndDesignerIdAndStatus(Long customerId, Long designerId, BookingStatus status);

    // ADM-11
    long countByDesignerId(Long designerId);

    long countByDesignerIdAndStatus(Long designerId, BookingStatus status);

    // PM-07 - customer demand signal per category: booking requests against projects in it
    long countByProjectCategoryId(Long categoryId);
}
