package com.rewatch.repository;

import java.time.Instant;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.rewatch.model.Trait;
import com.rewatch.model.TraitEvent;

@Repository
public interface TraitEventRepository extends JpaRepository<TraitEvent, Long> {

    List<TraitEvent> findByUserIdOrderByOccurredAtAscIdAsc(Long userId);

    List<TraitEvent> findByUserIdAndOccurredAtBetweenOrderByOccurredAtAscIdAsc(
            Long userId, Instant from, Instant to);

    List<TraitEvent> findByUserIdAndTraitOrderByOccurredAtAscIdAsc(Long userId, Trait trait);

    void deleteByUserId(Long userId);
}
