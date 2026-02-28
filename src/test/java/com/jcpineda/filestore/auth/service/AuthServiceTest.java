package com.jcpineda.filestore.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jcpineda.filestore.auth.domain.UserEntity;
import com.jcpineda.filestore.auth.persistence.UserRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordHasher passwordHasher;

    @InjectMocks
    private AuthService authService;

    @Test
    void registerCreatesUserWhenEmailDoesNotExist() {
        String rawEmail = " User@Example.com ";
        String normalizedEmail = "user@example.com";
        String password = "secret123";
        String hashedPassword = "hashed-password";

        when(userRepository.existsByEmail(normalizedEmail)).thenReturn(false);
        when(passwordHasher.hash(password)).thenReturn(hashedPassword);
        when(userRepository.save(any(UserEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AuthUser result = authService.register(new RegisterUserCommand(rawEmail, password));

        ArgumentCaptor<UserEntity> userCaptor = ArgumentCaptor.forClass(UserEntity.class);
        verify(userRepository).save(userCaptor.capture());

        UserEntity savedUser = userCaptor.getValue();
        assertThat(savedUser.getEmail()).isEqualTo(normalizedEmail);
        assertThat(savedUser.getPasswordHash()).isEqualTo(hashedPassword);

        assertThat(result.email()).isEqualTo(normalizedEmail);
        assertThat(result.userId()).isNotNull();
    }

    @Test
    void registerThrowsWhenEmailAlreadyExists() {
        String email = "user@example.com";

        when(userRepository.existsByEmail(email)).thenReturn(true);

        assertThatThrownBy(() -> authService.register(new RegisterUserCommand(email, "secret123")))
            .isInstanceOf(EmailAlreadyExistsException.class);
    }

    @Test
    void loginReturnsUserWhenCredentialsAreValid() {
        UUID userId = UUID.randomUUID();
        UserEntity existingUser = new UserEntity(userId, "user@example.com", "stored-hash");

        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(existingUser));
        when(passwordHasher.matches("secret123", "stored-hash")).thenReturn(true);

        AuthUser result = authService.login(new LoginCommand("user@example.com", "secret123"));

        assertThat(result.userId()).isEqualTo(userId);
        assertThat(result.email()).isEqualTo("user@example.com");
    }

    @Test
    void loginThrowsWhenUserIsMissing() {
        when(userRepository.findByEmail("missing@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new LoginCommand("missing@example.com", "secret123")))
            .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void loginThrowsWhenPasswordDoesNotMatch() {
        UserEntity existingUser = new UserEntity(UUID.randomUUID(), "user@example.com", "stored-hash");

        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(existingUser));
        when(passwordHasher.matches("wrong-password", "stored-hash")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(new LoginCommand("user@example.com", "wrong-password")))
            .isInstanceOf(InvalidCredentialsException.class);
    }
}
