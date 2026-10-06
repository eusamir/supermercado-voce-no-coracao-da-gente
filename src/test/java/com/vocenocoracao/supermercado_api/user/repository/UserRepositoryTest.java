package com.vocenocoracao.supermercado_api.user.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.vocenocoracao.supermercado_api.config.JpaConfig;
import com.vocenocoracao.supermercado_api.user.entity.User;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JpaConfig.class, UserRepositoryTest.PostgresConfig.class})
class UserRepositoryTest {

    @TestConfiguration(proxyBeanMethods = false)
    static class PostgresConfig {
        @Bean
        @ServiceConnection
        PostgreSQLContainer postgres() {
            return new PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"));
        }
    }

    @Autowired
    private UserRepository userRepository;

    private User user(UUID keycloakId, String email) {
        User user = new User();
        user.setKeycloakId(keycloakId);
        user.setName("Marta Oliveira");
        user.setEmail(email);
        return user;
    }

    @Test
    void findsTheUserByKeycloakId() {
        UUID keycloakId = UUID.randomUUID();
        userRepository.saveAndFlush(user(keycloakId, "marta@example.com"));

        assertThat(userRepository.findByKeycloakId(keycloakId)).isPresent();
        assertThat(userRepository.findByKeycloakId(UUID.randomUUID())).isEmpty();
    }

    @Test
    void emailExistenceCheckIgnoresCase() {
        userRepository.saveAndFlush(user(UUID.randomUUID(), "marta@example.com"));

        assertThat(userRepository.existsByEmailIgnoreCase("MARTA@Example.com")).isTrue();
        assertThat(userRepository.existsByEmailIgnoreCase("outra@example.com")).isFalse();
    }

    @Test
    void savingFillsTheAuditingDates() {
        User saved = userRepository.saveAndFlush(user(UUID.randomUUID(), "marta@example.com"));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
    }

    @Test
    void databaseRejectsDuplicatedKeycloakIdAndEmail() {
        UUID keycloakId = UUID.randomUUID();
        userRepository.saveAndFlush(user(keycloakId, "marta@example.com"));

        assertThatThrownBy(() -> userRepository.saveAndFlush(user(keycloakId, "outra@example.com")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
