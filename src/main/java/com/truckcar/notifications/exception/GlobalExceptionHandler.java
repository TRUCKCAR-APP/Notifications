package com.truckcar.notifications.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.TreeMap;

/**
 * Errores en formato RFC 7807 (ProblemDetail): type, title, status, detail e instance.
 * <p>
 * Se conservan como propiedades los campos de antes (message, error, details, timestamp) para no romper a los
 * clientes; un 400 de validación suma errors[] (field, message). Content-Type: application/problem+json solo para quien
 * lo pide en Accept; application/json para el resto (la PWA decide por ese tipo si interpreta el cuerpo como JSON).
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    static final String TYPE_BASE = "https://truckcar.co/errors/";

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleResourceNotFound(ResourceNotFoundException ex) {
        log.warn("Resource not found: {}", ex.getMessage());
        return problem(HttpStatus.NOT_FOUND, "not-found", "Recurso no encontrado", ex.getMessage());
    }

    @ExceptionHandler(InvalidApiKeyException.class)
    public ResponseEntity<ProblemDetail> handleInvalidApiKey(InvalidApiKeyException ex) {
        log.warn("Unauthorized API Key access: {}", ex.getMessage());
        return problem(HttpStatus.UNAUTHORIZED, "unauthorized", "API Key inválida", ex.getMessage());
    }

    @ExceptionHandler(NotificationDeliveryException.class)
    public ResponseEntity<ProblemDetail> handleNotificationDelivery(NotificationDeliveryException ex) {
        log.error("Notification delivery failure: {}", ex.getMessage(), ex);
        // El detalle de AWS queda en el log: no se expone al cliente.
        return problem(HttpStatus.BAD_GATEWAY, "delivery-error", "Error de entrega de la notificación",
                "No se pudo entregar la notificación por el canal solicitado");
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ProblemDetail> handleAccessDenied(AccessDeniedException ex) {
        log.warn("Access denied: {}", ex.getMessage());
        return problem(HttpStatus.FORBIDDEN, "forbidden", "Operación no permitida", "No tiene permisos para esta operación");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleValidationErrors(MethodArgumentNotValidException ex) {
        return validationProblem("Validación de datos fallida", ex);
    }

    /** JSON mal formado, UTF-8 inválido o un valor que no corresponde al tipo (enum desconocido): error del cliente. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ProblemDetail> handleUnreadableBody(HttpMessageNotReadableException ex) {
        log.debug("Cuerpo ilegible: {}", ex.getMessage());
        // Sin el detalle del parser en la respuesta: puede incluir fragmentos del cuerpo y nombres de clases internas.
        return problem(HttpStatus.BAD_REQUEST, "bad-request", "Solicitud inválida",
                "El cuerpo debe ser JSON válido en UTF-8 con los campos y valores esperados");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ProblemDetail> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return problem(HttpStatus.BAD_REQUEST, "bad-request", "Solicitud inválida",
                ex.getName() + ": valor con un tipo incorrecto");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleGeneral(Exception ex) {
        // Excepciones de Spring MVC con su propio estado (405, 415, parámetro faltante...): este handler genérico las
        // capturaría antes que el resolver por defecto y las convertiría en 500.
        if (ex instanceof ErrorResponse errorResponse && errorResponse.getStatusCode().is4xxClientError()) {
            return problem(errorResponse.getStatusCode(), "bad-request", "Solicitud inválida",
                    errorResponse.getBody().getDetail());
        }
        log.error("Unhandled exception: {}", ex.getMessage(), ex);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "internal-error", "Error interno",
                "No se pudo procesar la solicitud de notificaciones");
    }

    static ResponseEntity<ProblemDetail> validationProblem(String title, MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = new TreeMap<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.putIfAbsent(fe.getField(), fe.getDefaultMessage());
        }
        String detail = fieldErrors.isEmpty() ? "La solicitud tiene datos inválidos" : String.join("; ", fieldErrors.values());
        ResponseEntity<ProblemDetail> response = problem(HttpStatus.BAD_REQUEST, "validation-error", title, detail);
        response.getBody().setProperty("details", fieldErrors);
        response.getBody().setProperty("invalidParams", fieldErrors);
        response.getBody().setProperty("errors", fieldErrors.entrySet().stream()
                .map(e -> Map.of("field", e.getKey(), "message", e.getValue())).toList());
        return response;
    }

    /** ProblemDetail con los campos de antes (message, error, timestamp) para no romper a los clientes actuales. */
    static ResponseEntity<ProblemDetail> problem(HttpStatusCode status, String type, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create(TYPE_BASE + type));
        problem.setTitle(title);
        problem.setProperty("error", title);
        problem.setProperty("message", detail);
        problem.setProperty("timestamp", LocalDateTime.now());
        return ResponseEntity.status(status).contentType(negotiatedType()).body(problem);
    }

    /** application/problem+json solo para quien lo pide en Accept: Spring lo usaría también ante application/json. */
    private static MediaType negotiatedType() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            String accept = attributes.getRequest().getHeader("Accept");
            if (accept != null && accept.contains(MediaType.APPLICATION_PROBLEM_JSON_VALUE)) {
                return MediaType.APPLICATION_PROBLEM_JSON;
            }
        }
        return MediaType.APPLICATION_JSON;
    }
}
