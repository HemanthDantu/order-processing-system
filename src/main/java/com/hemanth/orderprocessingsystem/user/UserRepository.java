package com.hemanth.orderprocessingsystem.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository for user persistence operations.
 *
 * <p>Authentication only needs lookup-by-username today, so the repository
 * stays intentionally small and lets {@link JpaRepository} provide the rest.</p>
 */
public interface UserRepository extends JpaRepository<User, UUID> {

    /**
     * Finds a user by their unique username.
     *
     * @param username the login identifier used by the auth endpoint
     * @return an optional user if the username exists
     */
    Optional<User> findByUsername(String username);
}
