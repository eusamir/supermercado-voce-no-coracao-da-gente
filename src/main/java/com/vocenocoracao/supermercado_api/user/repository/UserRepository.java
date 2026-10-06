package com.vocenocoracao.supermercado_api.user.repository;

import com.vocenocoracao.supermercado_api.user.entity.User;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByKeycloakId(UUID keycloakId);

    boolean existsByEmailIgnoreCase(String email);
}
