package com.truckcar.notifications.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.net.URI;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ProblemDetail handleResourceNotFound(ResourceNotFoundException ex) {
        log.warn("Resource not found: {}", ex.getMessage());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problem.setTitle("Recurso no encontrado");
        problem.setType(URI.create("https://truckcar.com/errors/not-found"));
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }

    @ExceptionHandler(InvalidApiKeyException.class)
    public ProblemDetail handleInvalidApiKey(InvalidApiKeyException ex) {
        log.warn("Unauthorized API Key access: {}", ex.getMessage());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, ex.getMessage());
        problem.setTitle("API Key inválida");
        problem.setType(URI.create("https://truckcar.com/errors/unauthorized"));
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }

    @ExceptionHandler(NotificationDeliveryException.class)
    public ProblemDetail handleNotificationDelivery(NotificationDeliveryException ex) {
        log.error("Notification delivery failure: {}", ex.getMessage(), ex);
        // El detalle de AWS queda en el log: no se expone al cliente.
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY,
                "No se pudo entregar la notificación por el canal solicitado");
        problem.setTitle("Error de entrega de la notificación");
        problem.setType(URI.create("https://truckcar.com/errors/delivery-error"));
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidationErrors(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = new HashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.put(error.getField(), error.getDefaultMessage());
        }

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                fieldErrors.isEmpty() ? "La solicitud tiene datos inválidos"
                        : String.join("; ", new TreeMap<>(fieldErrors).values()));
        problem.setTitle("Validación de datos fallida");
        problem.setType(URI.create("https://truckcar.com/errors/validation-error"));
        problem.setProperty("invalidParams", fieldErrors);
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail handleAccessDenied(AccessDeniedException ex) {
        log.warn("Access denied: {}", ex.getMessage());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, ex.getMessage());
        problem.setTitle("Operación no permitida");
        problem.setType(URI.create("https://truckcar.com/errors/forbidden"));
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }

    /** Malformed JSON, invalid UTF-8 or a value of the wrong type (unknown enum): a client error, not a 500. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail handleUnreadableBody(HttpMessageNotReadableException ex) {
        log.debug("Unreadable request body: {}", ex.getMessage());
        // The parser detail stays in the log: it may echo parts of the body and internal class names.
        return badRequest("El cuerpo debe ser JSON válido en UTF-8 con los campos y valores esperados");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ProblemDetail handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return badRequest(ex.getName() + ": valor con un tipo incorrecto");
    }

    private static ProblemDetail badRequest(String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
        problem.setTitle("Solicitud inválida");
        problem.setType(URI.create("https://truckcar.com/errors/bad-request"));
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleGeneralException(Exception ex) {
        // Spring MVC exceptions that carry their own status (405, 415, missing parameter...): this catch-all would
        // otherwise turn them into a 500 before the default resolver sees them.
        if (ex instanceof ErrorResponse errorResponse && errorResponse.getStatusCode().is4xxClientError()) {
            ProblemDetail problem = errorResponse.getBody();
            problem.setTitle("Solicitud inválida");
            problem.setProperty("timestamp", Instant.now());
            return problem;
        }
        log.error("Unhandled exception: {}", ex.getMessage(), ex);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "No se pudo procesar la solicitud de notificaciones");
        problem.setTitle("Error interno");
        problem.setType(URI.create("https://truckcar.com/errors/internal"));
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }
}
