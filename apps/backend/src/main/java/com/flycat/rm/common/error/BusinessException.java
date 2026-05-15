package com.flycat.rm.common.error;

public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.slug());
        this.errorCode = errorCode;
    }

    public BusinessException(ErrorCode errorCode, String detail) {
        super(errorCode.slug() + ": " + detail);
        this.errorCode = errorCode;
    }

    public BusinessException(ErrorCode errorCode, String detail, Throwable cause) {
        super(errorCode.slug() + ": " + detail, cause);
        this.errorCode = errorCode;
    }

    public ErrorCode errorCode() {
        return errorCode;
    }
}
