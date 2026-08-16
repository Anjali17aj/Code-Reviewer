package com.codereview.repository;

import com.codereview.entity.CodebaseGroupFile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CodebaseGroupFileRepository extends JpaRepository<CodebaseGroupFile, Long> {

    List<CodebaseGroupFile> findByCodebaseGroupId(Long codebaseGroupId);

    void deleteByCodebaseGroupId(Long codebaseGroupId);
}
