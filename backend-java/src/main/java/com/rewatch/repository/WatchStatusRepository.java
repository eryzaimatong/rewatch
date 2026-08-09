package com.rewatch.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.rewatch.model.WatchStatus;

@Repository
public interface WatchStatusRepository extends JpaRepository<WatchStatus, Long> {

    Optional<WatchStatus> findByUserIdAndTitleId(Long userId, Long titleId);

    List<WatchStatus> findByUserId(Long userId);

    void deleteByUserIdAndTitleId(Long userId, Long titleId);

    void deleteByUserId(Long userId);
}
