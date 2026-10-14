package com.truckcar.notifications.controller;

import com.truckcar.notifications.dto.request.NotificationTemplateUpdateRequest;
import com.truckcar.notifications.dto.response.NotificationTemplateResponse;
import com.truckcar.notifications.service.NotificationTemplateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/templates")
@RequiredArgsConstructor
@Tag(name = "Templates", description = "Administración de plantillas de notificación i18n")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
public class NotificationTemplateController {

    private final NotificationTemplateService templateService;

    @GetMapping
    @Operation(summary = "Lista plantillas configuradas", description = "Retorna todas las plantillas de notificación registradas en el sistema")
    public ResponseEntity<List<NotificationTemplateResponse>> getAllTemplates() {
        return ResponseEntity.ok(templateService.getAllTemplates());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Detalle de una plantilla", description = "Obtiene los detalles de una plantilla por su identificador")
    public ResponseEntity<NotificationTemplateResponse> getTemplateById(@PathVariable UUID id) {
        return ResponseEntity.ok(templateService.getTemplateById(id));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Edita el texto de una plantilla", description = "Actualiza el texto con placeholders de una plantilla existente")
    public ResponseEntity<NotificationTemplateResponse> updateTemplate(
            @PathVariable UUID id,
            @Valid @RequestBody NotificationTemplateUpdateRequest request
    ) {
        NotificationTemplateResponse updated = templateService.updateTemplate(id, request);
        return ResponseEntity.ok(updated);
    }
}
