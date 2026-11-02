package com.csit314.platform.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDateTime;

// CUS-13 - one rating per (customer, designer); re-rating updates it rather than
// creating a second row
@Entity
@Table(name = "ratings", uniqueConstraints = @UniqueConstraint(columnNames = {"customer_id", "designer_id"}))
public class Rating {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @ManyToOne(optional = false)
    @JoinColumn(name = "designer_id", nullable = false)
    private InteriorDesigner designer;

    @Column(nullable = false)
    private int score;

    @Column(columnDefinition = "TEXT")
    private String comment;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected Rating() {
    }

    public Rating(Customer customer, InteriorDesigner designer, int score, String comment) {
        this.customer = customer;
        this.designer = designer;
        this.score = score;
        this.comment = comment;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    public void update(int score, String comment) {
        this.score = score;
        this.comment = comment;
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

    public int getScore() {
        return score;
    }

    public String getComment() {
        return comment;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
