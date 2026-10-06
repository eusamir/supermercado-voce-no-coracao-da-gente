package com.vocenocoracao.supermercado_api.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.vocenocoracao.supermercado_api.exceptions.AlreadyExistsException;
import com.vocenocoracao.supermercado_api.exceptions.InvalidRequestException;
import com.vocenocoracao.supermercado_api.exceptions.KeycloakIntegrationException;
import com.vocenocoracao.supermercado_api.user.entity.User;
import com.vocenocoracao.supermercado_api.user.integration.KeycloakAdminClient;
import com.vocenocoracao.supermercado_api.user.repository.UserRepository;
import com.vocenocoracao.supermercado_api.user.service.impl.UserServiceImpl;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.oauth2.jwt.Jwt;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private KeycloakAdminClient keycloakAdminClient;

    @InjectMocks
    private UserServiceImpl service;

    private User newUser(String name, String email) {
        User user = new User();
        user.setName(name);
        user.setEmail(email);
        return user;
    }

    private Jwt jwt(UUID subject, String email, String name, String preferredUsername) {
        Jwt.Builder builder = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject(subject.toString())
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300));
        if (email != null) {
            builder.claim("email", email);
        }
        if (name != null) {
            builder.claim("name", name);
        }
        if (preferredUsername != null) {
            builder.claim("preferred_username", preferredUsername);
        }
        return builder.build();
    }

    @Test
    void registerCreatesTheKeycloakUserAssignsTheRoleAndSavesLocally() {
        UUID keycloakId = UUID.randomUUID();
        User user = newUser("Marta Oliveira Santos", "marta@example.com");
        when(userRepository.existsByEmailIgnoreCase("marta@example.com")).thenReturn(false);
        when(keycloakAdminClient.createUser("marta@example.com", "Marta", "Oliveira Santos", "Senha1234"))
                .thenReturn(keycloakId);
        when(userRepository.saveAndFlush(user)).thenReturn(user);

        User registered = service.register(user, "Senha1234");

        assertThat(registered.getKeycloakId()).isEqualTo(keycloakId);
        InOrder order = inOrder(keycloakAdminClient, userRepository);
        order.verify(keycloakAdminClient).createUser("marta@example.com", "Marta", "Oliveira Santos", "Senha1234");
        order.verify(keycloakAdminClient).assignRealmRole(keycloakId, "CUSTOMER");
        order.verify(userRepository).saveAndFlush(user);
        verify(keycloakAdminClient, never()).deleteUser(any());
    }

    @Test
    void registerRejectsEmailAlreadySavedLocallyWithoutCallingKeycloak() {
        when(userRepository.existsByEmailIgnoreCase("marta@example.com")).thenReturn(true);

        assertThatThrownBy(() -> service.register(newUser("Marta Silva", "marta@example.com"), "Senha1234"))
                .isInstanceOf(AlreadyExistsException.class);
        verify(keycloakAdminClient, never()).createUser(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void registerPropagatesKeycloakConflictWithoutSavingOrCompensating() {
        when(keycloakAdminClient.createUser(anyString(), anyString(), anyString(), anyString()))
                .thenThrow(new AlreadyExistsException("E-mail já cadastrado."));

        assertThatThrownBy(() -> service.register(newUser("Marta Silva", "marta@example.com"), "Senha1234"))
                .isInstanceOf(AlreadyExistsException.class);
        verify(userRepository, never()).saveAndFlush(any());
        verify(keycloakAdminClient, never()).deleteUser(any());
    }

    @Test
    void registerDeletesTheKeycloakUserWhenTheRoleAssignmentFails() {
        UUID keycloakId = UUID.randomUUID();
        when(keycloakAdminClient.createUser(anyString(), anyString(), anyString(), anyString())).thenReturn(keycloakId);
        doThrow(new KeycloakIntegrationException("falha")).when(keycloakAdminClient)
                .assignRealmRole(keycloakId, "CUSTOMER");

        assertThatThrownBy(() -> service.register(newUser("Marta Silva", "marta@example.com"), "Senha1234"))
                .isInstanceOf(KeycloakIntegrationException.class);
        verify(keycloakAdminClient).deleteUser(keycloakId);
        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    void registerDeletesTheKeycloakUserWhenSavingLocallyFails() {
        UUID keycloakId = UUID.randomUUID();
        when(keycloakAdminClient.createUser(anyString(), anyString(), anyString(), anyString())).thenReturn(keycloakId);
        when(userRepository.saveAndFlush(any())).thenThrow(new IllegalStateException("banco fora"));

        assertThatThrownBy(() -> service.register(newUser("Marta Silva", "marta@example.com"), "Senha1234"))
                .isInstanceOf(IllegalStateException.class);
        verify(keycloakAdminClient).deleteUser(keycloakId);
    }

    @Test
    void registerConvertsConcurrentUniqueViolationIntoConflictAndCompensates() {
        UUID keycloakId = UUID.randomUUID();
        when(keycloakAdminClient.createUser(anyString(), anyString(), anyString(), anyString())).thenReturn(keycloakId);
        when(userRepository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("unique"));

        assertThatThrownBy(() -> service.register(newUser("Marta Silva", "marta@example.com"), "Senha1234"))
                .isInstanceOf(AlreadyExistsException.class);
        verify(keycloakAdminClient).deleteUser(keycloakId);
    }

    @Test
    void registerKeepsTheOriginalErrorWhenTheCompensationAlsoFails() {
        UUID keycloakId = UUID.randomUUID();
        when(keycloakAdminClient.createUser(anyString(), anyString(), anyString(), anyString())).thenReturn(keycloakId);
        when(userRepository.saveAndFlush(any())).thenThrow(new IllegalStateException("banco fora"));
        doThrow(new KeycloakIntegrationException("keycloak fora")).when(keycloakAdminClient).deleteUser(keycloakId);

        assertThatThrownBy(() -> service.register(newUser("Marta Silva", "marta@example.com"), "Senha1234"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("banco fora");
    }

    @Test
    void getCurrentUserReturnsTheExistingUser() {
        UUID keycloakId = UUID.randomUUID();
        User existing = newUser("Marta Silva", "marta@example.com");
        when(userRepository.findByKeycloakId(keycloakId)).thenReturn(Optional.of(existing));

        assertThat(service.getCurrentUser(jwt(keycloakId, "marta@example.com", "Marta Silva", null))).isSameAs(existing);
        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    void getCurrentUserCreatesTheUserOnFirstAccess() {
        UUID keycloakId = UUID.randomUUID();
        when(userRepository.findByKeycloakId(keycloakId)).thenReturn(Optional.empty());
        when(userRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        User created = service.getCurrentUser(jwt(keycloakId, "Marta@Example.com", "Marta Silva", "marta"));

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(captor.capture());
        assertThat(created).isSameAs(captor.getValue());
        assertThat(created.getKeycloakId()).isEqualTo(keycloakId);
        assertThat(created.getEmail()).isEqualTo("marta@example.com");
        assertThat(created.getName()).isEqualTo("Marta Silva");
    }

    @Test
    void getCurrentUserFallsBackToThePreferredUsernameWhenThereIsNoName() {
        UUID keycloakId = UUID.randomUUID();
        when(userRepository.findByKeycloakId(keycloakId)).thenReturn(Optional.empty());
        when(userRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        User created = service.getCurrentUser(jwt(keycloakId, "admin@admin.com", null, "admin"));

        assertThat(created.getName()).isEqualTo("admin");
    }

    @Test
    void getCurrentUserRejectsATokenWithoutEmail() {
        UUID keycloakId = UUID.randomUUID();
        when(userRepository.findByKeycloakId(keycloakId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getCurrentUser(jwt(keycloakId, null, "Marta Silva", null)))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void getCurrentUserReturnsTheWinnerWhenAConcurrentRequestCreatedTheUserFirst() {
        UUID keycloakId = UUID.randomUUID();
        User winner = newUser("Marta Silva", "marta@example.com");
        when(userRepository.findByKeycloakId(keycloakId)).thenReturn(Optional.empty(), Optional.of(winner));
        when(userRepository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("unique"));

        assertThat(service.getCurrentUser(jwt(keycloakId, "marta@example.com", "Marta Silva", null))).isSameAs(winner);
    }

    @Test
    void getCurrentUserReportsConflictWhenTheEmailBelongsToAnotherAccount() {
        UUID keycloakId = UUID.randomUUID();
        when(userRepository.findByKeycloakId(keycloakId)).thenReturn(Optional.empty());
        when(userRepository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("unique"));

        assertThatThrownBy(() -> service.getCurrentUser(jwt(keycloakId, "marta@example.com", "Marta Silva", null)))
                .isInstanceOf(AlreadyExistsException.class);
    }
}
