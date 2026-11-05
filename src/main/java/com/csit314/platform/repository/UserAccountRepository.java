package com.csit314.platform.repository;

import com.csit314.platform.model.AccountStatus;
import com.csit314.platform.model.Role;
import com.csit314.platform.model.UserAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface UserAccountRepository extends JpaRepository<UserAccount, Long> {

    Optional<UserAccount> findByEmail(String email);

    // ADM-01/09/13 - AuthService used to save() the whole account it loaded at the start
    // of a login. That's a merge of a detached entity (and, for a designer, its cascaded
    // Company), so anything an admin or the designer changed in between - verified flag,
    // status, company name, license number - was silently overwritten with the stale copy.
    // These only touch the login-tracking columns. Each runs in its own transaction on
    // purpose: AuthService isn't @Transactional so a failed attempt survives the exception
    //
    // ADM-09/13 - PR #30 review: the count has to be worked out in the database, not from
    // the copy AuthService read before its BCrypt check. Wrong guesses sent in parallel all
    // read the same count, so writing that back made 8 simultaneous guesses count as 1 and
    // the lockout never fired. An account inside an active lock is left alone, so more
    // guesses can't push its expiry out; a lapsed lock starts counting fresh from 1.
    // Native SQL because UserAccount is a JOINED hierarchy, where a JPQL bulk update can go
    // through a temporary table; these columns all live on user_accounts. The attempts
    // assignment must stay first: MySQL applies SET left to right, so it has to read
    // locked_at before that column is cleared
    @Transactional
    @Modifying
    @Query(value = "update user_accounts set "
            + "failed_login_attempts = case when locked_at is null "
            + "then failed_login_attempts + 1 else 1 end, "
            + "locked_at = null "
            + "where id = :id and (locked_at is null or locked_at <= :lockExpiredBefore)",
            nativeQuery = true)
    int countFailedLogin(@Param("id") Long id, @Param("lockExpiredBefore") LocalDateTime lockExpiredBefore);

    // separate from countFailedLogin so it sees the count after every parallel increment
    @Transactional
    @Modifying
    @Query(value = "update user_accounts set locked_at = :lockedAt "
            + "where id = :id and locked_at is null and failed_login_attempts >= :maxAttempts",
            nativeQuery = true)
    int lockIfOverLimit(@Param("id") Long id, @Param("maxAttempts") int maxAttempts,
                        @Param("lockedAt") LocalDateTime lockedAt);

    @Transactional
    @Modifying
    @Query("update UserAccount u set u.failedLoginAttempts = 0, u.lockedAt = null, "
            + "u.lastLoginAt = :loginAt where u.id = :id")
    int recordSuccessfulLogin(@Param("id") Long id, @Param("loginAt") LocalDateTime loginAt);

    boolean existsByEmail(String email);

    // ADM-06/08 - checked on every request behind AuthInterceptor; only the status is
    // needed, so this avoids loading the full account (and, for a designer, the joined
    // subclass and its eager company) just to check one field
    boolean existsByIdAndStatus(Long id, AccountStatus status);

    boolean existsByRole(Role role);

    // Anthony's review - lets AdminAccountInitializer/PlatformManagerAccountInitializer
    // check whether an already-seeded account's password hash still matches the old,
    // now-public hardcoded default, on an instance that was running before that
    // fallback was removed
    Optional<UserAccount> findFirstByRole(Role role);

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
