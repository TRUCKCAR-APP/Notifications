package com.truckcar.notifications.exception;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Errores RFC 7807 con message/error en la raíz (antes faltaban) y application/json por defecto (antes problem+json). */
class ProblemDetailContractTest {

    @RestController
    static class ProbeController {
        record Body(@NotNull(message = "El id de la carga es obligatorio") Long loadId) { }

        @GetMapping("/notifications/forbidden")
        String forbidden() {
            throw new org.springframework.security.access.AccessDeniedException("Access Denied");
        }

        @PostMapping("/notifications/validate")
        String validate(@Valid @RequestBody Body body) {
            return "ok";
        }

        @GetMapping("/notifications/boom")
        String boom() {
            throw new IllegalStateException("detalle interno");
        }
    }

    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new ProbeController())
            .setControllerAdvice(new GlobalExceptionHandler())
            .setMessageConverters(new MappingJackson2HttpMessageConverter(
                    org.springframework.http.converter.json.Jackson2ObjectMapperBuilder.json().build()))
            .build();

    @Test
    void anErrorHasTheStandardFieldsAndTheOldOnesAtTheRoot() throws Exception {
        mvc.perform(get("/notifications/forbidden").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.type").value("https://truckcar.co/errors/forbidden"))
                .andExpect(jsonPath("$.title").value("Operación no permitida"))
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.instance").value("/notifications/forbidden"))
                .andExpect(jsonPath("$.detail").value("No tiene permisos para esta operación"))
                .andExpect(jsonPath("$.message").value("No tiene permisos para esta operación"))
                .andExpect(jsonPath("$.error").value("Operación no permitida"))
                .andExpect(jsonPath("$.properties").doesNotExist());
    }

    @Test
    void problemJsonOnlyForWhoAsksForIt() throws Exception {
        mvc.perform(get("/notifications/forbidden").accept(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        mvc.perform(get("/notifications/forbidden")).andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
    }

    @Test
    void aValidationErrorListsTheFieldsInDetailsAndErrors() throws Exception {
        mvc.perform(post("/notifications/validate").accept(MediaType.APPLICATION_JSON).contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.loadId").value("El id de la carga es obligatorio"))
                .andExpect(jsonPath("$.errors[0].field").value("loadId"))
                .andExpect(jsonPath("$.errors[0].message").value("El id de la carga es obligatorio"))
                .andExpect(jsonPath("$.message").value("El id de la carga es obligatorio"));
    }

    @Test
    void anUnexpectedErrorDoesNotLeakInternals() throws Exception {
        mvc.perform(get("/notifications/boom").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.type").value("https://truckcar.co/errors/internal-error"))
                .andExpect(content().string(not(containsString("detalle interno"))));
    }
}
