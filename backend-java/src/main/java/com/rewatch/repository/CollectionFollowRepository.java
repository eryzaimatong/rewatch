package com.rewatch.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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

    /** One query for every candidate folder's follower count — see SocialService.discoverCollections. */
    @Query("select f.folderId as folderId, count(f) as cnt from CollectionFollow f where f.folderId in :folderIds group by f.folderId")
    List<WatchlistItemRepository.FolderCount> countByFolderIdIn(@Param("folderIds") List<Long> folderIds);

    /** Which of these candidate folders the caller already follows, in one query instead of one existsByUserIdAndFolderId call per folder. */
    @Query("select f.folderId from CollectionFollow f where f.userId = :userId and f.folderId in :folderIds")
    List<Long> findFolderIdsFollowedByUser(@Param("userId") Long userId, @Param("folderIds") List<Long> folderIds);
}
