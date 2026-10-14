package com.truckcar.notifications.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Slf4j
@Component
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    private static final String API_KEY_HEADER = "X-API-KEY";

    // Sin valor por defecto: si TRUCKCAR_NOTIFICATIONS_API_KEY no está definida, la autenticación por API key queda deshabilitada.
    @Value("${app.security.api-key:}")
    private String configuredApiKey;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String apiKeyHeader = request.getHeader(API_KEY_HEADER);

        if (StringUtils.hasText(configuredApiKey) && StringUtils.hasText(apiKeyHeader)
                && apiKeyHeader.equals(configuredApiKey)) {
            UserPrincipal servicePrincipal = UserPrincipal.builder()
                    .email("internal-service@truckcar.co")
                    .role("INTERNAL_SERVICE")
                    .authorities(List.of(new SimpleGrantedAuthority("ROLE_INTERNAL_SERVICE")))
                    .build();

            UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                    servicePrincipal,
                    apiKeyHeader,
                    servicePrincipal.getAuthorities()
            );

            SecurityContextHolder.getContext().setAuthentication(auth);
        }

        filterChain.doFilter(request, response);
    }
}
