package com.codereview.repository;

import com.codereview.entity.ReviewFile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReviewFileRepository extends JpaRepository<ReviewFile, Long> {

    List<ReviewFile> findByReviewId(Long reviewId);

    void deleteByReviewId(Long reviewId);
}
