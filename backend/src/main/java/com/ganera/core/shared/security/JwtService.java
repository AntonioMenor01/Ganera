package com.ganera.core.shared.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

@Service
public class JwtService {

    private final SecretKey signingKey;
    private final long expirationMinutes;

    public JwtService(
            @Value("${ganera.jwt.secret}") String secret,
            @Value("${ganera.jwt.expiration-minutes}") long expirationMinutes) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("JWT_SECRET no está configurada. La aplicación no puede arrancar sin ella.");
        }
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMinutes = expirationMinutes;
    }

    public String generarToken(GaneraUserPrincipal principal) {
        Instant ahora = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(principal.usuarioId()))
                .claim("gestoriaId", principal.gestoriaId())
                .claim("email", principal.email())
                .issuedAt(Date.from(ahora))
                .expiration(Date.from(ahora.plus(Duration.ofMinutes(expirationMinutes))))
                .signWith(signingKey)
                .compact();
    }

    public GaneraUserPrincipal parsearToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        return new GaneraUserPrincipal(
                Long.valueOf(claims.getSubject()),
                claims.get("gestoriaId", Long.class),
                claims.get("email", String.class));
    }
}
