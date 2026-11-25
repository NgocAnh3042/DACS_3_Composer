package com.example.backend.api;

import jakarta.validation.ConstraintViolationException;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiErrorResponse> handleApiException(ApiException exception) {
        return error(exception.getStatus(), exception.getMessage());
    }

    @ExceptionHandler({HttpMessageNotReadableException.class})
    public ResponseEntity<ApiErrorResponse> handleMalformedJson(Exception ignored) {
        return error(HttpStatus.BAD_REQUEST, "JSON không hợp lệ");
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, ConstraintViolationException.class,
            HandlerMethodValidationException.class})
    public ResponseEntity<ApiErrorResponse> handleValidation(Exception ignored) {
        return error(HttpStatus.UNPROCESSABLE_ENTITY, "Dữ liệu không hợp lệ");
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNotFound(Exception ignored) {
        return error(HttpStatus.NOT_FOUND, "Không tìm thấy endpoint");
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiErrorResponse> handleMethodNotAllowed(Exception ignored) {
        return error(HttpStatus.METHOD_NOT_ALLOWED, "HTTP method không được hỗ trợ");
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiErrorResponse> handleAuthentication(Exception ignored) {
        return error(HttpStatus.UNAUTHORIZED, "Chưa xác thực");
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiErrorResponse> handleAccessDenied(Exception ignored) {
        return error(HttpStatus.FORBIDDEN, "Không có quyền thực hiện thao tác");
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ApiErrorResponse> handleDatabaseUnavailable(Exception ignored) {
        return error(HttpStatus.SERVICE_UNAVAILABLE, "Dịch vụ dữ liệu tạm thời không khả dụng");
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiErrorResponse> handleResponseStatus(ResponseStatusException exception) {
        HttpStatus status = HttpStatus.resolve(exception.getStatusCode().value());
        if (status == null || status.is5xxServerError()) {
            return error(HttpStatus.INTERNAL_SERVER_ERROR, "Lỗi xử lý yêu cầu");
        }
        return error(status, exception.getReason() == null ? status.getReasonPhrase() : exception.getReason());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(Exception ignored) {
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "Lỗi xử lý yêu cầu");
    }

    private ResponseEntity<ApiErrorResponse> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(new ApiErrorResponse(message));
    }
}
