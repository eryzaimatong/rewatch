package com.rewatch.repository;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.rewatch.model.User;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    User findByEmail(String email);
    User findByUsername(String username);

    /**
     * Friend search: username only, not email. Matching on email would let
     * anyone probe whether a specific address has an account here (the same
     * enumeration leak forgot-password's always-succeeds response is designed
     * to avoid) — see AuthController/PasswordResetService for that precedent.
     */
    List<User> findByUsernameContainingIgnoreCase(String username, Pageable pageable);
}