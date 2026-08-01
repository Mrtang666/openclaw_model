package com.example.spring.xhs.console;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;
import java.util.UUID;

@RestControllerAdvice(assignableTypes = XhsConsoleController.class)
public class XhsConsoleExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> badRequest(IllegalArgumentException exception) {
        return error(HttpStatus.BAD_REQUEST, "XHS_INVALID_REQUEST", exception);
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> conflict(IllegalStateException exception) {
        return error(HttpStatus.CONFLICT, "XHS_OPERATION_REJECTED", exception);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> internal(Exception exception) {
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "XHS_INTERNAL_ERROR", exception);
    }

    private ResponseEntity<Map<String, Object>> error(HttpStatus status, String code, Exception exception) {
        String requestId = UUID.randomUUID().toString();
        String message = exception.getMessage() == null || exception.getMessage().isBlank()
                ? "操作失败"
                : exception.getMessage();
        return ResponseEntity.status(status).body(Map.of(
                "code", code,
                "message", message,
                "requestId", requestId));
    }
}
