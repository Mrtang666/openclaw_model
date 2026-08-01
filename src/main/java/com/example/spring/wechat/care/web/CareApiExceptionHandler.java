package com.example.spring.wechat.care.web;

import com.example.spring.wechat.care.exception.CareException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

import java.util.UUID;

@RestControllerAdvice(basePackages = "com.example.spring.wechat.care.web")
public class CareApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(CareApiExceptionHandler.class);

    @ExceptionHandler(CareException.class)
    public ResponseEntity<CareApiResponse<Void>> handleCare(CareException exception, WebRequest request) {
        String traceId = traceId(request);
        return ResponseEntity.status(exception.code().httpStatus())
                .body(CareApiResponse.error(exception.code().name(), exception.getMessage(), traceId));
    }

    @ExceptionHandler(DuplicateKeyException.class)
    public ResponseEntity<CareApiResponse<Void>> handleDuplicate(DuplicateKeyException exception, WebRequest request) {
        String traceId = traceId(request);
        log.info("照护数据重复提交，traceId={}", traceId);
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(CareApiResponse.error("CONFLICT", "数据已存在，请刷新后重试", traceId));
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<CareApiResponse<Void>> handleMissingHeader(
            MissingRequestHeaderException exception,
            WebRequest request) {
        String traceId = traceId(request);
        HttpStatus status = "Authorization".equalsIgnoreCase(exception.getHeaderName())
                ? HttpStatus.UNAUTHORIZED
                : HttpStatus.BAD_REQUEST;
        String code = status == HttpStatus.UNAUTHORIZED ? "UNAUTHORIZED" : "INVALID_ARGUMENT";
        return ResponseEntity.status(status)
                .body(CareApiResponse.error(code, "缺少请求头: " + exception.getHeaderName(), traceId));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<CareApiResponse<Void>> handleTypeMismatch(
            MethodArgumentTypeMismatchException exception,
            WebRequest request) {
        String traceId = traceId(request);
        return ResponseEntity.badRequest().body(CareApiResponse.error(
                "INVALID_ARGUMENT", "请求参数格式错误: " + exception.getName(), traceId));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<CareApiResponse<Void>> handleUnexpected(Exception exception, WebRequest request) {
        String traceId = traceId(request);
        log.error("照护接口处理失败，traceId={}", traceId, exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(CareApiResponse.error("INTERNAL_ERROR", "服务暂时不可用，请稍后重试", traceId));
    }

    private String traceId(WebRequest request) {
        String header = request.getHeader("X-Request-Id");
        return header == null || header.isBlank() ? UUID.randomUUID().toString() : header.strip();
    }
}
