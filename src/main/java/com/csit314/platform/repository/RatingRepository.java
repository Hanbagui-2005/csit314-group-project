package com.csit314.platform.repository;

import com.csit314.platform.model.Rating;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RatingRepository extends JpaRepository<Rating, Long> {

    Optional<Rating> findByCustomerIdAndDesignerId(Long customerId, Long designerId);

    List<Rating> findByDesignerIdOrderByCreatedAtDesc(Long designerId);
}
