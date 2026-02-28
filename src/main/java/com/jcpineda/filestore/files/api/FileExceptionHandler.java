package com.jcpineda.filestore.files.api;

import com.jcpineda.filestore.common.api.ApiErrorResponse;
import com.jcpineda.filestore.common.api.CorrelationIdResolver;
import com.jcpineda.filestore.files.service.FileAccessDeniedException;
import com.jcpineda.filestore.files.service.FileNotFoundException;
import com.jcpineda.filestore.files.service.InvalidFileStateException;
import com.jcpineda.filestore.files.service.InvalidFileUploadException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class FileExceptionHandler {

    @ExceptionHandler(FileNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNotFound(HttpServletRequest request, FileNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(ApiErrorResponse.of("FILE_NOT_FOUND", ex.getMessage(), CorrelationIdResolver.resolve(request)));
    }

    @ExceptionHandler(FileAccessDeniedException.class)
    public ResponseEntity<ApiErrorResponse> handleForbidden(HttpServletRequest request, FileAccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body(ApiErrorResponse.of("FORBIDDEN", ex.getMessage(), CorrelationIdResolver.resolve(request)));
    }

    @ExceptionHandler(InvalidFileStateException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidState(HttpServletRequest request, InvalidFileStateException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(ApiErrorResponse.of("INVALID_FILE_STATE", ex.getMessage(), CorrelationIdResolver.resolve(request)));
    }

    @ExceptionHandler(InvalidFileUploadException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidUpload(HttpServletRequest request, InvalidFileUploadException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(ApiErrorResponse.of("VALIDATION_ERROR", ex.getMessage(), CorrelationIdResolver.resolve(request)));
    }
}
