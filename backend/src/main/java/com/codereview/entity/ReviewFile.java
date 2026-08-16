package com.codereview.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "review_files", indexes = {
    @Index(name = "idx_reviewfile_review", columnList = "reviewId")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewFile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long reviewId;

    @Column(nullable = false)
    private Long fileId;

    @Column(nullable = false)
    private String filePath;
}
