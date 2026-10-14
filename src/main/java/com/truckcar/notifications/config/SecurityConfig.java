package com.truckcar.notifications.config;

import com.truckcar.notifications.security.ApiKeyAuthenticationFilter;
import com.truckcar.notifications.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final ApiKeyAuthenticationFilter apiKeyAuthenticationFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Public actuator & openapi
                        .requestMatchers(
                                "/actuator/**",
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html"
                        ).permitAll()

                        // Direct send: requires internal service API Key
                        .requestMatchers(HttpMethod.POST, "/api/v1/notifications/send")
                        .hasAuthority("ROLE_INTERNAL_SERVICE")

                        // Admin & Coordinator notification lookup by id
                        .requestMatchers(HttpMethod.GET, "/api/v1/notifications/{id}")
                        .hasAnyAuthority("ROLE_ADMIN", "ROLE_COORDINADOR")

                        // User notification history (accessible by authenticated user or admin/coordinator)
                        .requestMatchers(HttpMethod.GET, "/api/v1/notifications/user/**")
                        .authenticated()

                        // Templates endpoints: ADMIN only
                        .requestMatchers("/api/v1/templates/**")
                        .hasAuthority("ROLE_ADMIN")

                        .anyRequest().authenticated()
                )
                .addFilterBefore(apiKeyAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
