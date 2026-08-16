package com.codereview.repository;

import com.codereview.entity.CodeFile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CodeFileRepository extends JpaRepository<CodeFile, Long> {

    List<CodeFile> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<CodeFile> findByUserIdAndFolderIdOrderByUpdatedAtDesc(Long userId, Long folderId);

    List<CodeFile> findByUserIdAndFolderIdIsNullOrderByCreatedAtDesc(Long userId);

    List<CodeFile> findByUserIdAndFolderIdIsNullOrderByUpdatedAtDesc(Long userId);

    void deleteByUserIdAndFolderId(Long userId, Long folderId);
}
