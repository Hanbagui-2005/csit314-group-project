package com.csit314.platform.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

// PM-15 - an unexpected (unhandled) exception, captured so Platform Management can
// monitor platform health instead of only finding out when a user reports it
@Entity
@Table(name = "system_error_logs")
public class SystemErrorLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "request_path", nullable = false, length = 300)
    private String requestPath;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String details;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private LocalDateTime occurredAt;

    protected SystemErrorLog() {
    }

    public SystemErrorLog(String requestPath, String details) {
        this.requestPath = requestPath;
        this.details = details;
    }

    @PrePersist
    void onCreate() {
        if (occurredAt == null) {
            occurredAt = LocalDateTime.now();
        }
    }

    public Long getId() {
        return id;
    }

    public String getRequestPath() {
        return requestPath;
    }

    public String getDetails() {
        return details;
    }

    public LocalDateTime getOccurredAt() {
        return occurredAt;
    }
}
