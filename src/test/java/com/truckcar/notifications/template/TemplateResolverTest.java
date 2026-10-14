package com.truckcar.notifications.template;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TemplateResolverTest {

    private TemplateResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new TemplateResolver();
    }

    @Test
    @DisplayName("Should replace all matching placeholders with variable values")
    void shouldReplacePlaceholders() {
        String template = "Tu carga {{loadId}} fue reasignada a {{ruta}} por {{motivo}}.";
        Map<String, Object> vars = Map.of(
                "loadId", "CRG-1029",
                "ruta", "Bogotá - Medellín",
                "motivo", "mantenimiento en La Línea"
        );

        String result = resolver.resolve(template, vars);

        assertThat(result).isEqualTo("Tu carga CRG-1029 fue reasignada a Bogotá - Medellín por mantenimiento en La Línea.");
    }

    @Test
    @DisplayName("Should replace missing placeholders with empty string")
    void shouldReplaceMissingPlaceholdersWithEmpty() {
        String template = "Alerta: {{alerta}} en {{lugar}}.";
        Map<String, Object> vars = Map.of("alerta", "Derrumbe");

        String result = resolver.resolve(template, vars);

        assertThat(result).isEqualTo("Alerta: Derrumbe en .");
    }

    @Test
    @DisplayName("Should return raw text if variables map is empty or null")
    void shouldReturnRawTextWhenNoVariables() {
        String template = "Sin placeholders.";
        assertThat(resolver.resolve(template, null)).isEqualTo(template);
        assertThat(resolver.resolve(template, Map.of())).isEqualTo(template);
    }
}
