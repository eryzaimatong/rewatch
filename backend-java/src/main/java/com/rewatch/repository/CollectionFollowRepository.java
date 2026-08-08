package com.rewatch.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.rewatch.model.CollectionFollow;

@Repository
public interface CollectionFollowRepository extends JpaRepository<CollectionFollow, Long> {

    boolean existsByUserIdAndFolderId(Long userId, Long folderId);

    long countByFolderId(Long folderId);

    List<CollectionFollow> findByUserIdOrderByCreatedAtDesc(Long userId);

    void deleteByUserIdAndFolderId(Long userId, Long folderId);

    void deleteByFolderId(Long folderId);

    void deleteByUserId(Long userId);
}
