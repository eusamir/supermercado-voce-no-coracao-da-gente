package com.vocenocoracao.supermercado_api.user.service.impl;

import com.vocenocoracao.supermercado_api.exceptions.AlreadyExistsException;
import com.vocenocoracao.supermercado_api.exceptions.InvalidRequestException;
import com.vocenocoracao.supermercado_api.user.entity.User;
import com.vocenocoracao.supermercado_api.user.integration.KeycloakAdminClient;
import com.vocenocoracao.supermercado_api.user.repository.UserRepository;
import com.vocenocoracao.supermercado_api.user.service.UserService;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

@Service
public class UserServiceImpl implements UserService {

    private static final Logger log = LoggerFactory.getLogger(UserServiceImpl.class);
    private static final String CUSTOMER_ROLE = "CUSTOMER";
    private static final String EMAIL_ALREADY_EXISTS = "E-mail já cadastrado.";

    private final UserRepository userRepository;
    private final KeycloakAdminClient keycloakAdminClient;

    public UserServiceImpl(UserRepository userRepository, KeycloakAdminClient keycloakAdminClient) {
        this.userRepository = userRepository;
        this.keycloakAdminClient = keycloakAdminClient;
    }

    @Override
    public User register(User user, String password) {
        if (userRepository.existsByEmailIgnoreCase(user.getEmail())) {
            throw new AlreadyExistsException(EMAIL_ALREADY_EXISTS);
        }

        String[] nameParts = user.getName().split("\\s+", 2);
        UUID keycloakId = keycloakAdminClient.createUser(user.getEmail(), nameParts[0], nameParts[1], password);

        try {
            keycloakAdminClient.assignRealmRole(keycloakId, CUSTOMER_ROLE);
            user.setKeycloakId(keycloakId);
            return userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException exception) {
            rollbackKeycloakUser(keycloakId);
            throw new AlreadyExistsException(EMAIL_ALREADY_EXISTS);
        } catch (RuntimeException exception) {
            rollbackKeycloakUser(keycloakId);
            throw exception;
        }
    }

    @Override
    public User getCurrentUser(Jwt jwt) {
        UUID keycloakId = UUID.fromString(jwt.getSubject());

        return userRepository.findByKeycloakId(keycloakId)
                .orElseGet(() -> createFromToken(jwt, keycloakId));
    }

    private User createFromToken(Jwt jwt, UUID keycloakId) {
        String email = jwt.getClaimAsString("email");

        if (email == null || email.isBlank()) {
            throw new InvalidRequestException("O token não possui e-mail.");
        }

        String name = jwt.getClaimAsString("name");

        if (name == null || name.isBlank()) {
            name = jwt.getClaimAsString("preferred_username");
        }

        User user = new User();
        user.setKeycloakId(keycloakId);
        user.setName(name);
        user.setEmail(email.trim().toLowerCase());

        try {
            return userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException exception) {
            return userRepository.findByKeycloakId(keycloakId)
                    .orElseThrow(() -> new AlreadyExistsException(EMAIL_ALREADY_EXISTS));
        }
    }

    private void rollbackKeycloakUser(UUID keycloakId) {
        try {
            keycloakAdminClient.deleteUser(keycloakId);
        } catch (RuntimeException exception) {
            log.error("Falha ao desfazer o usuário {} no Keycloak; remoção manual necessária", keycloakId, exception);
        }
    }
}
