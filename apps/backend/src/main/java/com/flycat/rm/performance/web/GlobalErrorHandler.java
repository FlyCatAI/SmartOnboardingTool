package com.flycat.rm.performance.web;

import com.flycat.rm.common.error.BusinessException;
import com.flycat.rm.common.error.ErrorCode;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Maps {@link BusinessException} and common Spring binding errors to the
 * {@code { code, slug, message }} envelope and an HTTP status that matches
 * {@link ErrorCode}.
 */
@RestControllerAdvice
public class GlobalErrorHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiEnvelope<Void>> handleBusiness(BusinessException ex) {
        ErrorCode code = ex.errorCode();
        HttpStatus status = httpStatus(code);
        return ResponseEntity.status(status)
                .body(ApiEnvelope.error(code.wireCode(), code.slug(), ex.getMessage()));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiEnvelope<Void>> handleMissingParam(
            MissingServletRequestParameterException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiEnvelope.error(
                        ErrorCode.BAD_REQUEST.code(),
                        ErrorCode.BAD_REQUEST.slug(),
                        "missing required parameter: " + ex.getParameterName()));
    }

    private static HttpStatus httpStatus(ErrorCode code) {
        return switch (code) {
            case E_RM_PERF_FORBIDDEN, FORBIDDEN, ROLE_MISMATCH, DATA_SCOPE_DENIED -> HttpStatus.FORBIDDEN;
            case BAD_REQUEST -> HttpStatus.BAD_REQUEST;
            case UNAUTHENTICATED -> HttpStatus.UNAUTHORIZED;
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case CONFLICT -> HttpStatus.CONFLICT;
            case RATE_LIMITED -> HttpStatus.TOO_MANY_REQUESTS;
            default -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }
}
