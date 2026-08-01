package com.rewatch.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.rewatch.model.Title;

@Repository
public interface TitleRepository extends JpaRepository<Title, Long> {
}