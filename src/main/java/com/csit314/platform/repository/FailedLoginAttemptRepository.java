package com.csit314.platform.repository;

import com.csit314.platform.model.FailedLoginAttempt;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FailedLoginAttemptRepository extends JpaRepository<FailedLoginAttempt, Long> {

    List<FailedLoginAttempt> findTop50ByOrderByAttemptedAtDesc();
}
