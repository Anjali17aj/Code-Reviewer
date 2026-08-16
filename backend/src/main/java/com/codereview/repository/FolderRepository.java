package com.codereview.repository;

import com.codereview.entity.Folder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FolderRepository extends JpaRepository<Folder, Long> {

    List<Folder> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<Folder> findByUserIdAndParentIdOrderByCreatedAtDesc(Long userId, Long parentId);

    List<Folder> findByUserIdAndParentIdIsNullOrderByCreatedAtDesc(Long userId);
}
