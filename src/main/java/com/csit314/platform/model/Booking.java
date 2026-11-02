package com.csit314.platform.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.time.LocalDateTime;

// CUS-11 - a customer asking to talk before committing to hire
@Entity
@Table(name = "bookings")
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @ManyToOne(optional = false)
    @JoinColumn(name = "designer_id", nullable = false)
    private InteriorDesigner designer;

    @ManyToOne
    @JoinColumn(name = "project_id")
    private Project project;

    @Column(name = "preferred_date", nullable = false)
    private LocalDate preferredDate;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private BookingStatus status = BookingStatus.PENDING;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected Booking() {
    }

    public Booking(Customer customer, InteriorDesigner designer, Project project, LocalDate preferredDate, String notes) {
        this.customer = customer;
        this.designer = designer;
        this.project = project;
        this.preferredDate = preferredDate;
        this.notes = notes;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    public void accept() {
        status = BookingStatus.ACCEPTED;
    }

    public void decline() {
        status = BookingStatus.DECLINED;
    }

    // ADM-11 - cancellation rate signal; only makes sense before the designer has responded
    public void cancel() {
        if (status != BookingStatus.PENDING && status != BookingStatus.ACCEPTED) {
            throw new IllegalStateException("Only a pending or accepted booking can be cancelled.");
        }
        status = BookingStatus.CANCELLED;
    }

    public Long getId() {
        return id;
    }

    public Customer getCustomer() {
        return customer;
    }

    public InteriorDesigner getDesigner() {
        return designer;
    }

    public Project getProject() {
        return project;
    }

    public LocalDate getPreferredDate() {
        return preferredDate;
    }

    public String getNotes() {
        return notes;
    }

    public BookingStatus getStatus() {
        return status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
