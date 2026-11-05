package com.csit314.platform.repository;

import com.csit314.platform.model.Booking;
import com.csit314.platform.model.BookingStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    // CUS-11 - a customer cancelling and the designer accepting/declining the same
    // booking at the same moment used to both read PENDING, both pass the state check,
    // and whichever committed last silently won (e.g. a cancelled booking ending up
    // ACCEPTED). Taking a row lock makes the second request wait and then see the first
    // one's result, so Booking's own state rules reject it instead
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from Booking b where b.id = :id")
    Optional<Booking> findByIdForUpdate(@Param("id") Long id);

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
