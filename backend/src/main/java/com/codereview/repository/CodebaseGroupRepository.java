package com.codereview.repository;

import com.codereview.entity.CodebaseGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CodebaseGroupRepository extends JpaRepository<CodebaseGroup, Long> {

    List<CodebaseGroup> findByUserIdOrderByCreatedAtDesc(Long userId);
}
