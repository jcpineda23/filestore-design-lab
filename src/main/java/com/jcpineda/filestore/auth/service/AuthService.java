package com.jcpineda.filestore.auth.service;

import com.jcpineda.filestore.auth.domain.UserEntity;
import com.jcpineda.filestore.auth.persistence.UserRepository;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordHasher passwordHasher;

    public AuthService(UserRepository userRepository, PasswordHasher passwordHasher) {
        this.userRepository = userRepository;
        this.passwordHasher = passwordHasher;
    }

    @Transactional
    public AuthUser register(RegisterUserCommand command) {
        String normalizedEmail = normalizeEmail(command.email());

        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new EmailAlreadyExistsException(normalizedEmail);
        }

        UserEntity user = new UserEntity(
            UUID.randomUUID(),
            normalizedEmail,
            passwordHasher.hash(command.password())
        );

        UserEntity savedUser = userRepository.save(user);
        return new AuthUser(savedUser.getId(), savedUser.getEmail());
    }

    @Transactional(readOnly = true)
    public AuthUser login(LoginCommand command) {
        String normalizedEmail = normalizeEmail(command.email());

        UserEntity user = userRepository.findByEmail(normalizedEmail)
            .orElseThrow(InvalidCredentialsException::new);

        if (!passwordHasher.matches(command.password(), user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }

        return new AuthUser(user.getId(), user.getEmail());
    }

    private String normalizeEmail(String email) {
        return email == null
            ? ""
            : email.trim().toLowerCase(Locale.ROOT);
    }
}
