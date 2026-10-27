package com.csit314.platform.repository;

import com.csit314.platform.model.AccountStatus;
import com.csit314.platform.model.Role;
import com.csit314.platform.model.UserAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface UserAccountRepository extends JpaRepository<UserAccount, Long> {

    Optional<UserAccount> findByEmail(String email);

    boolean existsByEmail(String email);

    boolean existsByRole(Role role);

    long countByRoleAndStatus(Role role, AccountStatus status);

    // ADM-14 - everyone who signed up from this IP recently, to spot a registration burst
    List<UserAccount> findByRegistrationIpAndCreatedAtAfter(String registrationIp, LocalDateTime since);

    // PM-05
    long countByRoleAndCreatedAtAfter(Role role, LocalDateTime since);
}
