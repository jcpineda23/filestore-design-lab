package com.jcpineda.filestore.security;

import com.jcpineda.filestore.auth.service.AuthUser;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

    private static final String CLAIM_EMAIL = "email";

    private final SecretKey key;
    private final long expirationSeconds;

    public JwtService(JwtProperties properties) {
        this.key = buildSigningKey(properties.secret());
        this.expirationSeconds = properties.expirationSeconds();
    }

    public TokenDetails issueToken(AuthUser authUser) {
        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plusSeconds(expirationSeconds);

        String token = Jwts.builder()
            .subject(authUser.userId().toString())
            .claim(CLAIM_EMAIL, authUser.email())
            .issuedAt(Date.from(issuedAt))
            .expiration(Date.from(expiresAt))
            .signWith(key)
            .compact();

        return new TokenDetails(token, expiresAt);
    }

    public JwtPrincipal parseToken(String token) {
        Claims claims = Jwts.parser()
            .verifyWith(key)
            .build()
            .parseSignedClaims(token)
            .getPayload();

        String subject = claims.getSubject();
        String email = claims.get(CLAIM_EMAIL, String.class);

        return new JwtPrincipal(UUID.fromString(subject), email);
    }

    private SecretKey buildSigningKey(String rawSecret) {
        try {
            byte[] decoded = Decoders.BASE64.decode(rawSecret);
            return Keys.hmacShaKeyFor(decoded);
        } catch (RuntimeException ex) {
            byte[] bytes = rawSecret.getBytes(StandardCharsets.UTF_8);
            return Keys.hmacShaKeyFor(bytes);
        }
    }

    public record TokenDetails(String token, Instant expiresAt) {
    }
}
