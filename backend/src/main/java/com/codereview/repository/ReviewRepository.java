package com.codereview.repository;

import com.codereview.entity.Review;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ReviewRepository extends JpaRepository<Review, Long> {

    List<Review> findByUserIdOrderByCreatedAtDesc(Long userId);

    Page<Review> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    Page<Review> findByUserIdAndLanguageOrderByCreatedAtDesc(Long userId, String language, Pageable pageable);

    // Date range filtering
    Page<Review> findByUserIdAndCreatedAtBetweenOrderByCreatedAtDesc(
            Long userId, LocalDateTime startDate, LocalDateTime endDate, Pageable pageable);

    // Language + date range
    Page<Review> findByUserIdAndLanguageAndCreatedAtBetweenOrderByCreatedAtDesc(
            Long userId, String language, LocalDateTime startDate, LocalDateTime endDate, Pageable pageable);

    // Assessment filtering
    Page<Review> findByUserIdAndOverallRatingOrderByCreatedAtDesc(
            Long userId, String overallRating, Pageable pageable);

    // Language + assessment
    Page<Review> findByUserIdAndLanguageAndOverallRatingOrderByCreatedAtDesc(
            Long userId, String language, String overallRating, Pageable pageable);

    // Date range + assessment
    Page<Review> findByUserIdAndCreatedAtBetweenAndOverallRatingOrderByCreatedAtDesc(
            Long userId, LocalDateTime startDate, LocalDateTime endDate, String overallRating, Pageable pageable);

    // All filters combined
    Page<Review> findByUserIdAndLanguageAndCreatedAtBetweenAndOverallRatingOrderByCreatedAtDesc(
            Long userId, String language, LocalDateTime startDate, LocalDateTime endDate, String overallRating, Pageable pageable);
}
