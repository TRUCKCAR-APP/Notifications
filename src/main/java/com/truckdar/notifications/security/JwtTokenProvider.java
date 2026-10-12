package com.truckdar.notifications.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
public class JwtTokenProvider {

    private final SecretKey secretKey;

    /**
     * Mismo secreto y misma decodificación (Base64 estricto) que User-Identity y api-gateway:
     * el token emitido por User-Identity debe validar aquí sin configuración adicional.
     */
    public JwtTokenProvider(@Value("${app.jwt.secret}") String secret) {
        try {
            this.secretKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
        } catch (RuntimeException e) {
            throw new IllegalStateException(
                    "TRUCKDAR_JWT_SECRET debe ser un valor Base64 de al menos 256 bits, igual al de User-Identity", e);
        }
    }

    public boolean validateToken(String token) {
        try {
            Jwts.parser().verifyWith(secretKey).build().parseSignedClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("Invalid JWT token: {}", e.getMessage());
            return false;
        }
    }

    public Authentication getAuthentication(String token) {
        Claims claims = Jwts.parser().verifyWith(secretKey).build().parseSignedClaims(token).getPayload();

        String userIdStr = claims.getSubject();
        UUID userId = null;
        if (userIdStr != null) {
            try {
                userId = UUID.fromString(userIdStr);
            } catch (IllegalArgumentException ignored) {
            }
        }

        String email = claims.get("email", String.class);
        String role = claims.get("role", String.class);

        List<SimpleGrantedAuthority> authorities = Collections.emptyList();
        if (role != null && !role.isBlank()) {
            String roleName = role.startsWith("ROLE_") ? role : "ROLE_" + role;
            authorities = List.of(new SimpleGrantedAuthority(roleName));
        }

        UserPrincipal principal = UserPrincipal.builder()
                .id(userId)
                .email(email)
                .role(role)
                .authorities(authorities)
                .build();

        return new UsernamePasswordAuthenticationToken(principal, token, authorities);
    }
}
