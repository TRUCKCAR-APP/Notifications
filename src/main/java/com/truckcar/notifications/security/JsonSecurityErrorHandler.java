package com.truckcar.notifications.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 401 y 403 de la cadena de seguridad, que no pasan por el @RestControllerAdvice. Mismo formato RFC 7807 que
 * GlobalExceptionHandler: type, title, status, detail e instance, más message, error y timestamp para la PWA.
 * Content-Type: application/problem+json solo para quien lo pide en Accept; application/json para el resto.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JsonSecurityErrorHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        write(request, response, HttpStatus.UNAUTHORIZED, "Usuario no autenticado",
                "Se requiere un token válido o la API Key de servicio interno", "unauthorized");
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        log.warn("Acceso denegado a {} {}", request.getMethod(), request.getRequestURI());
        write(request, response, HttpStatus.FORBIDDEN, "Operación no permitida",
                "No tiene permisos para acceder a este recurso", "forbidden");
    }

    private void write(HttpServletRequest request, HttpServletResponse response, HttpStatus status, String title,
                       String detail, String type) throws IOException {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", "https://truckcar.co/errors/" + type);
        body.put("title", title);
        body.put("status", status.value());
        body.put("detail", detail);
        body.put("instance", request.getRequestURI());
        body.put("error", title);
        body.put("message", detail);
        body.put("timestamp", Instant.now().toString());
        String accept = request.getHeader("Accept");
        response.setStatus(status.value());
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(accept != null && accept.contains(MediaType.APPLICATION_PROBLEM_JSON_VALUE)
                ? MediaType.APPLICATION_PROBLEM_JSON_VALUE : MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
