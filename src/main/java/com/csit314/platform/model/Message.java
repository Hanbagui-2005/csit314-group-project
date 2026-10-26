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

import java.time.LocalDateTime;

// IDR-13 - one-way: a customer messaging a designer about a project. The designer
// "responds" however they like outside the platform - no in-app reply thread here,
// that would need a customer-facing inbox too, which is bigger scope (see commit notes)
@Entity
@Table(name = "messages")
public class Message {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "sender_id", nullable = false)
    private Customer sender;

    @ManyToOne(optional = false)
    @JoinColumn(name = "recipient_id", nullable = false)
    private InteriorDesigner recipient;

    // optional - a general enquiry doesn't have to be about one specific project
    @ManyToOne
    @JoinColumn(name = "project_id")
    private Project project;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String body;

    @Column(name = "sent_at", nullable = false, updatable = false)
    private LocalDateTime sentAt;

    // "read" is a reserved word in MySQL, breaks CREATE TABLE if used as the column name
    @Column(name = "is_read", nullable = false)
    private boolean read = false;

    protected Message() {
    }

    public Message(Customer sender, InteriorDesigner recipient, Project project, String body) {
        this.sender = sender;
        this.recipient = recipient;
        this.project = project;
        this.body = body;
    }

    @PrePersist
    void onCreate() {
        if (sentAt == null) {
            sentAt = LocalDateTime.now();
        }
    }

    public void markRead() {
        this.read = true;
    }

    public Long getId() {
        return id;
    }

    public Customer getSender() {
        return sender;
    }

    public InteriorDesigner getRecipient() {
        return recipient;
    }

    public Project getProject() {
        return project;
    }

    public String getBody() {
        return body;
    }

    public LocalDateTime getSentAt() {
        return sentAt;
    }

    public boolean isRead() {
        return read;
    }
}
