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

    // ADM-06/08 - checked on every request behind AuthInterceptor; only the status is
    // needed, so this avoids loading the full account (and, for a designer, the joined
    // subclass and its eager company) just to check one field
    boolean existsByIdAndStatus(Long id, AccountStatus status);

    boolean existsByRole(Role role);

    long countByRoleAndStatus(Role role, AccountStatus status);

    // ADM-14 - everyone who signed up from this IP recently, to spot a registration burst
    List<UserAccount> findByRegistrationIpAndCreatedAtAfter(String registrationIp, LocalDateTime since);

    // PM-02/PM-14 - new accounts (any role) created within an arbitrary window, for the
    // growth forecast's monthly history and the anomaly check's daily baseline. Half-open
    // [start, end) - Anthony's review: Between is inclusive on both ends, which would
    // double-count a row created exactly at a shared window boundary
    long countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(LocalDateTime start, LocalDateTime end);

    // PM-06/PM-08/PM-12 - new accounts of one role within an arbitrary (half-open)
    // window, for both the calendar-period reports and the scheduled single-day report
    long countByRoleAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(Role role, LocalDateTime start, LocalDateTime end);
}
