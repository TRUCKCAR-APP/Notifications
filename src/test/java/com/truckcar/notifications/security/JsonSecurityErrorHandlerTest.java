package com.truckcar.notifications.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.InsufficientAuthenticationException;

import static org.assertj.core.api.Assertions.assertThat;

/** Los 401/403 de la cadena de seguridad llevan cuerpo JSON en español, no vacío. */
class JsonSecurityErrorHandlerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final JsonSecurityErrorHandler handler = new JsonSecurityErrorHandler(objectMapper);

    @Test
    void forbiddenHasAJsonBody() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        handler.handle(new MockHttpServletRequest("POST", "/api/v1/notifications/send"), response,
                new AccessDeniedException("Access Denied"));

        assertThat(response.getStatus()).isEqualTo(403);
        // Sin Accept problem+json: application/json, que es lo que la PWA interpreta como JSON.
        assertThat(response.getContentType()).startsWith("application/json");
        JsonNode body = objectMapper.readTree(response.getContentAsByteArray());
        assertThat(body.get("status").asInt()).isEqualTo(403);
        assertThat(body.get("title").asText()).isEqualTo("Operación no permitida");
        assertThat(body.get("detail").asText()).isNotBlank();
        assertThat(body.get("message").asText()).isEqualTo(body.get("detail").asText());
        assertThat(body.get("instance").asText()).isEqualTo("/api/v1/notifications/send");
    }

    @Test
    void problemJsonOnlyForWhoAsksForIt() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/notifications/user/x");
        request.addHeader("Accept", "application/problem+json");
        MockHttpServletResponse response = new MockHttpServletResponse();
        handler.commence(request, response, new InsufficientAuthenticationException("x"));

        assertThat(response.getContentType()).startsWith("application/problem+json");
    }

    @Test
    void unauthenticatedHasAJsonBody() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        handler.commence(new MockHttpServletRequest("POST", "/api/v1/notifications/send"), response,
                new InsufficientAuthenticationException("Full authentication is required"));

        assertThat(response.getStatus()).isEqualTo(401);
        JsonNode body = objectMapper.readTree(response.getContentAsByteArray());
        assertThat(body.get("title").asText()).isEqualTo("Usuario no autenticado");
    }
}
