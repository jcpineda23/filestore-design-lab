package com.jcpineda.filestore.auth.api;

import com.jcpineda.filestore.auth.service.AuthService;
import com.jcpineda.filestore.auth.service.AuthUser;
import com.jcpineda.filestore.auth.service.LoginCommand;
import com.jcpineda.filestore.auth.service.RegisterUserCommand;
import com.jcpineda.filestore.security.JwtService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final JwtService jwtService;

    public AuthController(AuthService authService, JwtService jwtService) {
        this.authService = authService;
        this.jwtService = jwtService;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        AuthUser user = authService.register(new RegisterUserCommand(request.email(), request.password()));
        JwtService.TokenDetails token = jwtService.issueToken(user);
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(new AuthResponse(user.userId(), token.token(), token.expiresAt()));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        AuthUser user = authService.login(new LoginCommand(request.email(), request.password()));
        JwtService.TokenDetails token = jwtService.issueToken(user);
        return ResponseEntity.ok(new AuthResponse(user.userId(), token.token(), token.expiresAt()));
    }
}
