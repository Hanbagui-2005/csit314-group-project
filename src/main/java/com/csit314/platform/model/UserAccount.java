package com.csit314.platform.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

// shared login details for every account type; each subclass gets its own table joined on id
@Entity
@Table(name = "user_accounts")
@Inheritance(strategy = InheritanceType.JOINED)
public abstract class UserAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    // BCrypt hash, never the raw password
    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30, updatable = false)
    private Role role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AccountStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;

    // ADM-09/13 - locked after too many failed logins, regardless of account status
    @Column(name = "failed_login_attempts", nullable = false)
    private int failedLoginAttempts = 0;

    @Column(name = "locked_at")
    private LocalDateTime lockedAt;

    // ADM-14 - where they signed up from, so clusters of sign-ups can be flagged
    @Column(name = "registration_ip", length = 45)
    private String registrationIp;

    protected UserAccount() {
    }

    protected UserAccount(Role role, String email, String passwordHash, AccountStatus status) {
        this.role = role;
        this.email = email;
        this.passwordHash = passwordHash;
        this.status = status;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    // name shown in the admin user list and the navbar
    public abstract String getDisplayName();

    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public Role getRole() {
        return role;
    }

    public AccountStatus getStatus() {
        return status;
    }

    public void setStatus(AccountStatus status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getLastLoginAt() {
        return lastLoginAt;
    }

    public void setLastLoginAt(LocalDateTime lastLoginAt) {
        this.lastLoginAt = lastLoginAt;
    }

    public static final int MAX_FAILED_ATTEMPTS = 5;
    // ADM-09/13 - a lock expires on its own after this long, so 5 wrong guesses at any
    // account (including the only admin) can't permanently lock it out with no recovery
    // path other than an admin manually unlocking it (or editing the database)
    public static final long LOCK_DURATION_MINUTES = 15;

    public void recordFailedLogin() {
        if (lockedAt != null) {
            if (isLocked()) {
                // still within an active lock - further wrong guesses shouldn't keep
                // pushing the 15-minute expiry back out, or a locked account could be
                // kept locked indefinitely just by someone continuing to guess wrong
                return;
            }
            // the previous lock has already lapsed - start counting fresh instead of
            // letting the stale count immediately re-lock the account after just one
            // more mistake
            failedLoginAttempts = 0;
            lockedAt = null;
        }
        failedLoginAttempts++;
        if (failedLoginAttempts >= MAX_FAILED_ATTEMPTS) {
            lockedAt = LocalDateTime.now();
        }
    }

    public void clearFailedLogins() {
        failedLoginAttempts = 0;
        lockedAt = null;
    }

    public boolean isLocked() {
        return lockedAt != null && lockedAt.plusMinutes(LOCK_DURATION_MINUTES).isAfter(LocalDateTime.now());
    }

    public int getFailedLoginAttempts() {
        return failedLoginAttempts;
    }

    public LocalDateTime getLockedAt() {
        return lockedAt;
    }

    public String getRegistrationIp() {
        return registrationIp;
    }

    public void setRegistrationIp(String registrationIp) {
        this.registrationIp = registrationIp;
    }
}
