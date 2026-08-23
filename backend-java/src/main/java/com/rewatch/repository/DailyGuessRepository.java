package com.rewatch.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.rewatch.model.DailyGuess;

@Repository
public interface DailyGuessRepository extends JpaRepository<DailyGuess, Long> {

    Optional<DailyGuess> findByUserIdAndPlayDate(Long userId, LocalDate playDate);

    List<DailyGuess> findByUserIdOrderByPlayDateDesc(Long userId);

    void deleteByUserId(Long userId);
}
