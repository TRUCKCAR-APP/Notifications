package com.truckcar.notifications.controller;

import com.truckcar.notifications.dto.request.DirectNotificationSendRequest;
import com.truckcar.notifications.dto.response.NotificationResponse;
import com.truckcar.notifications.security.UserPrincipal;
import com.truckcar.notifications.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
@Tag(name = "Notifications", description = "Endpoints de consulta y envío directo de notificaciones")
public class NotificationController {

    private final NotificationService notificationService;

    @PostMapping("/send")
    @Operation(summary = "Envío directo de notificación", description = "Permite a otros microservicios enviar una notificación síncrona/directa protegida por API Key",
            security = @SecurityRequirement(name = "apiKeyAuth"))
    public ResponseEntity<NotificationResponse> sendDirect(
            @Valid @RequestBody DirectNotificationSendRequest request
    ) {
        NotificationResponse response = notificationService.sendDirect(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/user/{userId}")
    @Operation(summary = "Historial de notificaciones de un usuario", description = "Lista paginada de notificaciones para un usuario específico",
            security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<Page<NotificationResponse>> getUserNotifications(
            @PathVariable UUID userId,
            @AuthenticationPrincipal UserPrincipal principal,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        // Enforce user privacy: user can only query their own notifications unless they are ADMIN or COORDINADOR
        if (principal != null) {
            boolean isStaff = principal.getAuthorities().stream()
                    .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN") || a.getAuthority().equals("ROLE_COORDINADOR"));
            if (!isStaff && !userId.equals(principal.getId())) {
                throw new AccessDeniedException("No tiene permisos para ver las notificaciones de otro usuario");
            }
        }

        Page<NotificationResponse> result = notificationService.getNotificationsByUserId(userId, pageable);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_COORDINADOR')")
    @Operation(summary = "Detalle de una notificación puntual", description = "Consulta el detalle y estado de entrega de una notificación específica",
            security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<NotificationResponse> getNotificationById(@PathVariable UUID id) {
        NotificationResponse response = notificationService.getNotificationById(id);
        return ResponseEntity.ok(response);
    }
}
