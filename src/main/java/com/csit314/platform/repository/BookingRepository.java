package com.csit314.platform.repository;

import com.csit314.platform.model.Booking;
import com.csit314.platform.model.BookingStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    List<Booking> findByDesignerIdOrderByCreatedAtDesc(Long designerId);

    // PM-06/PM-08/PM-12 - new bookings within an arbitrary (half-open) window, for both
    // the calendar-period and scheduled single-day reports
    long countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(LocalDateTime start, LocalDateTime end);

    List<Booking> findByCustomerIdOrderByCreatedAtDesc(Long customerId);

    // CUS-13 - the closest thing this app has to "actually worked with this designer"
    boolean existsByCustomerIdAndDesignerIdAndStatus(Long customerId, Long designerId, BookingStatus status);

    // CUS-13 - also requires the booking date to have passed, so a customer can't rate a
    // consultation that hasn't happened yet
    boolean existsByCustomerIdAndDesignerIdAndStatusAndPreferredDateLessThanEqual(
            Long customerId, Long designerId, BookingStatus status, LocalDate onOrBefore);

    // ADM-10 - a complaint requires having actually requested a booking with the designer
    boolean existsByCustomerIdAndDesignerId(Long customerId, Long designerId);

    // ADM-11
    long countByDesignerId(Long designerId);

    long countByDesignerIdAndStatus(Long designerId, BookingStatus status);

    // PM-07 - customer demand signal per category: booking requests against projects in it
    long countByProjectCategoryId(Long categoryId);
}
