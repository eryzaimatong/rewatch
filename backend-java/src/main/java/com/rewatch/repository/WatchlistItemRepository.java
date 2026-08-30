package com.rewatch.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.rewatch.model.WatchlistItem;

@Repository
public interface WatchlistItemRepository extends JpaRepository<WatchlistItem, Long> {

    List<WatchlistItem> findByUserIdOrderByAddedAtDesc(Long userId);

    Optional<WatchlistItem> findByUserIdAndTitleId(Long userId, Long titleId);

    Optional<WatchlistItem> findByIdAndUserId(Long id, Long userId);

    boolean existsByUserIdAndTitleId(Long userId, Long titleId);

    long countByUserId(Long userId);

    List<WatchlistItem> findByFolderIdOrderByAddedAtDesc(Long folderId);

    /** One query for every candidate folder's item count, instead of one countByFolderId call per folder — see SocialService.discoverCollections. */
    @Query("select i.folderId as folderId, count(i) as cnt from WatchlistItem i where i.folderId in :folderIds group by i.folderId")
    List<FolderCount> countByFolderIdIn(@Param("folderIds") List<Long> folderIds);

    /** Newest-first items for a whole batch of folders in one query, grouped client-side by folderId — see SocialService.discoverCollections. */
    List<WatchlistItem> findByFolderIdInOrderByAddedAtDesc(List<Long> folderIds);

    void deleteByUserId(Long userId);

    interface FolderCount {
        Long getFolderId();
        Long getCnt();
    }
}
