package com.csit314.platform.repository;

import com.csit314.platform.model.LicenseVerification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LicenseVerificationRepository
        extends JpaRepository<LicenseVerification, Long> {

    List<LicenseVerification>
            findByDesignerIdOrderByRecordedAtDescIdDesc(Long designerId);
}
