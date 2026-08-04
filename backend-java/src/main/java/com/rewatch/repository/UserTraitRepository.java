package com.rewatch.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.rewatch.model.UserTrait;

@Repository
public interface UserTraitRepository extends JpaRepository<UserTrait, Long> {

    List<UserTrait> findByUserId(Long userId);

    void deleteByUserId(Long userId);
}
