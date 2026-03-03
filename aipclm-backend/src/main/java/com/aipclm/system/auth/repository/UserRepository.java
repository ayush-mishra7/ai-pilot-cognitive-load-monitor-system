package com.aipclm.system.auth.repository;

import com.aipclm.system.auth.model.User;
import com.aipclm.system.auth.model.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    boolean existsByCallSign(String callSign);

    long countByRole(UserRole role);
}
