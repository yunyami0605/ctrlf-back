package com.ctrlf.education.exception;

import com.ctrlf.common.dto.ApiError;
import com.ctrlf.common.exception.BusinessException;
import com.ctrlf.common.exception.EntityNotFoundException;
import com.ctrlf.common.exception.ValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.converter.HttpMessageNotReadableException;

/**
 * 교육 서비스 전역 예외 처리기.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * 공통 BusinessException 처리.
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiError> handleBusinessException(BusinessException ex) {
        log.debug("Business exception: {}", ex.getMessage());

        String error = ex.getStatus() != null 
            ? ex.getStatus().getReasonPhrase() 
            : "Bad Request";
        return ResponseEntity.status(ex.getStatus() != null ? ex.getStatus() : HttpStatus.BAD_REQUEST)
            .body(new ApiError(error, ex.getMessage()));
    }

    /**
     * EntityNotFoundException 처리 (404).
     * 도메인별 예외(EducationNotFoundException, VideoNotFoundException 등)도 포함.
     */
    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<ApiError> handleEntityNotFound(EntityNotFoundException ex) {
        log.debug("Entity not found: {}", ex.getMessage());

        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(new ApiError("Not Found", ex.getMessage()));
    }

    /**
     * 공통 ValidationException 처리 (400).
     */
    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<ApiError> handleValidationException(ValidationException ex) {
        log.debug("Validation exception: {}", ex.getMessage());
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(new ApiError("Bad Request", ex.getMessage()));
    }

    /**
     * ResponseStatusException 처리.
     */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiError> handle(ResponseStatusException ex) {
        HttpStatus status = HttpStatus.resolve(ex.getStatusCode().value());
        String error = status != null ? status.getReasonPhrase() : "Error";
        String message = ex.getReason();
        return ResponseEntity.status(ex.getStatusCode())
            .body(new ApiError(error, message));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex) {
        String msg = ex.getBindingResult() != null && ex.getBindingResult().getFieldError() != null
            ? ex.getBindingResult().getFieldError().getDefaultMessage()
            : "Validation failed";
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(new ApiError("Bad Request", msg));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(new ApiError("Bad Request", ex.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> handleBadRequest(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(new ApiError("Bad Request", ex.getMessage()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleHttpMessageNotReadable(HttpMessageNotReadableException ex) {
        String message = ex.getMessage() != null 
            ? "Invalid request format: " + ex.getMessage()
            : "Invalid request format";
        log.warn("Invalid request format: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(new ApiError("Bad Request", message));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception ex) {
        log.error("Unexpected error", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(new ApiError("Internal Server Error", "Unexpected error"));
    }
}