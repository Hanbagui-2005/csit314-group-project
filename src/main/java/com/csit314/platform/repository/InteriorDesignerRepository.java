package com.csit314.platform.repository;

import com.csit314.platform.model.AccountStatus;
import com.csit314.platform.model.InteriorDesigner;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InteriorDesignerRepository
        extends JpaRepository<InteriorDesigner, Long> {

    List<InteriorDesigner> findByStatus(AccountStatus status);

    List<InteriorDesigner> findByCompanyLicenseNumberIgnoreCaseAndIdNot(
            String licenseNumber, Long designerId);
}
