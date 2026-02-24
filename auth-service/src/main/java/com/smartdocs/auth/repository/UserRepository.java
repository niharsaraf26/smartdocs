package com.smartdocs.auth.repository;

import com.smartdocs.auth.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * Find user by email (used for authentication)
     */
    Optional<User> findByEmail(String email);

    /**
     * Check if user exists with given email (used for registration validation)
     */
    boolean existsByEmail(String email);
}
