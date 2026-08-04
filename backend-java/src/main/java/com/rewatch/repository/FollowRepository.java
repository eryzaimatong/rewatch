package com.rewatch.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.rewatch.model.Follow;

@Repository
public interface FollowRepository extends JpaRepository<Follow, Long> {

    boolean existsByFollowerIdAndFolloweeId(Long followerId, Long followeeId);

    void deleteByFollowerIdAndFolloweeId(Long followerId, Long followeeId);

    List<Follow> findByFollowerIdOrderByCreatedAtDesc(Long followerId);

    List<Follow> findByFolloweeIdOrderByCreatedAtDesc(Long followeeId);

    long countByFollowerId(Long followerId);

    long countByFolloweeId(Long followeeId);
}
