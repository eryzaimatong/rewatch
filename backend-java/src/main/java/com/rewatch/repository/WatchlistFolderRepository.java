package com.rewatch.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.rewatch.model.WatchlistFolder;

@Repository
public interface WatchlistFolderRepository extends JpaRepository<WatchlistFolder, Long> {

    List<WatchlistFolder> findByUserIdOrderByNameAsc(Long userId);

    Optional<WatchlistFolder> findByUserIdAndName(Long userId, String name);

    boolean existsByIdAndUserId(Long id, Long userId);

    void deleteByUserId(Long userId);
}
