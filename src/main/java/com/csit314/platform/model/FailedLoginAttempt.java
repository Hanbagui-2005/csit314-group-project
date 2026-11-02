package com.csit314.platform.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

// ADM-13 - one row per failed login, kept even if the email doesn't match a real
// account (that's useful signal too - someone guessing emails) so no FK to UserAccount
@Entity
@Table(name = "failed_login_attempts")
public class FailedLoginAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String email;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "attempted_at", nullable = false, updatable = false)
    private LocalDateTime attemptedAt;

    protected FailedLoginAttempt() {
    }

    public FailedLoginAttempt(String email, String ipAddress) {
        this.email = email;
        this.ipAddress = ipAddress;
    }

    @PrePersist
    void onCreate() {
        if (attemptedAt == null) {
            attemptedAt = LocalDateTime.now();
        }
    }

    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public LocalDateTime getAttemptedAt() {
        return attemptedAt;
    }
}
