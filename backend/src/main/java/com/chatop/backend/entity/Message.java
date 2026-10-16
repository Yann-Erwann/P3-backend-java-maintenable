package com.chatop.backend.entity;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

import jakarta.persistence.*;

@Entity
@Table(name = "MESSAGES")
public class Message {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "rental_id", nullable = false)
    private Integer rentalId;

    @Column(name = "user_id", nullable = false)
    private Integer userId;

    @Column(nullable = false, length = 2000)
    private String message;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected Message() { }

    public Message(Integer rentalId, Integer userId, String message) {
        this.rentalId = rentalId;
        this.userId = userId;
        this.message = message;
    }

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now(ZoneOffset.UTC);
    }

    public Integer getId() { return id; }
    public Integer getRentalId() { return rentalId; }
    public Integer getUserId() { return userId; }
    public String getMessage() { return message; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
