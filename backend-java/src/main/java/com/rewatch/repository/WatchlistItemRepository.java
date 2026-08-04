package com.rewatch.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
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
}
