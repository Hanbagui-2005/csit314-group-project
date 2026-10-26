package com.csit314.platform.repository;

import com.csit314.platform.model.Booking;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    List<Booking> findByDesignerIdOrderByCreatedAtDesc(Long designerId);

    List<Booking> findByCustomerIdOrderByCreatedAtDesc(Long customerId);
}
