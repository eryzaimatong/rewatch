package com.rewatch.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.rewatch.model.TasteDNA;

@Repository
public interface TasteDNARepository extends JpaRepository<TasteDNA, Long> {
    TasteDNA findByUserId(Long userId);
}