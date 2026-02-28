package com.jcpineda.filestore.auth.api;

import com.jcpineda.filestore.auth.service.EmailAlreadyExistsException;
import com.jcpineda.filestore.auth.service.InvalidCredentialsException;
import com.jcpineda.filestore.common.api.ApiErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class AuthExceptionHandler {

    @ExceptionHandler(EmailAlreadyExistsException.class)
    public ResponseEntity<ApiErrorResponse> handleEmailAlreadyExists(HttpServletRequest request,
                                                                     EmailAlreadyExistsException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(ApiErrorResponse.of("EMAIL_ALREADY_EXISTS", ex.getMessage(), resolveCorrelationId(request)));
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidCredentials(HttpServletRequest request,
                                                                     InvalidCredentialsException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
            .body(ApiErrorResponse.of("UNAUTHORIZED", ex.getMessage(), resolveCorrelationId(request)));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(HttpServletRequest request,
                                                             MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult()
            .getFieldErrors()
            .stream()
            .findFirst()
            .map(FieldError::getDefaultMessage)
            .orElse("Validation failed");

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(ApiErrorResponse.of("VALIDATION_ERROR", message, resolveCorrelationId(request)));
    }

    private String resolveCorrelationId(HttpServletRequest request) {
        String correlationId = request.getHeader("X-Correlation-Id");
        return correlationId == null || correlationId.isBlank()
            ? UUID.randomUUID().toString()
            : correlationId;
    }
}
