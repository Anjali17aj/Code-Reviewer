package com.codereview.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "reviews", indexes = {
    @Index(name = "idx_review_user_created", columnList = "userId, createdAt")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Review {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private String language;

    @Column(nullable = false)
    private String sourceType;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String codeInput;

    @Column(columnDefinition = "TEXT")
    private String reviewResult;

    private String overallRating;

    private int criticalCount;

    private int warningCount;

    private int suggestionCount;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;
}
