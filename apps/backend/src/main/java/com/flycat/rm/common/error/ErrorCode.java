package com.flycat.rm.common.error;

/**
 * 统一错误码。每个 spec 中的失败场景在这里登记一行。
 * 命名约定：&lt;capability&gt;.&lt;scenario&gt; → 4 位数字 + 短英文标识。
 */
public enum ErrorCode {

    // 通用
    INTERNAL_ERROR("0500", "internal_error"),
    BAD_REQUEST("0400", "bad_request"),
    UNAUTHENTICATED("0401", "unauthenticated"),
    FORBIDDEN("0403", "forbidden"),
    NOT_FOUND("0404", "not_found"),
    CONFLICT("0409", "conflict"),
    RATE_LIMITED("0429", "rate_limited"),

    // auth-and-identity
    SSO_LOGIN_FAILED("1001", "sso_login_failed"),
    SESSION_EXPIRED("1002", "session_expired"),
    SESSION_REFRESH_FAILED("1003", "session_refresh_failed"),
    OTP_REQUIRED("1010", "otp_required"),
    OTP_INVALID("1011", "otp_invalid"),
    OTP_EXPIRED("1012", "otp_expired"),
    OTP_RATE_LIMITED("1013", "otp_rate_limited"),

    // RBAC
    ROLE_MISMATCH("1101", "role_mismatch"),
    DATA_SCOPE_DENIED("1102", "data_scope_denied"),

    // merchant-management
    MERCHANT_NOT_FOUND("2001", "merchant_not_found"),
    FOLLOWUP_EDIT_WINDOW_EXPIRED("2010", "followup_edit_window_expired"),
    FOLLOWUP_IMAGE_LIMIT_EXCEEDED("2011", "followup_image_limit_exceeded"),
    SENSITIVE_FIELD_DECRYPT_DENIED("2020", "sensitive_field_decrypt_denied"),

    // task-management
    TASK_NOT_FOUND("3001", "task_not_found"),
    TASK_ILLEGAL_TRANSITION("3010", "task_illegal_transition"),
    TASK_CLAIM_CONFLICT("3011", "task_claim_conflict"),
    TASK_REASSIGN_PENDING("3020", "task_reassign_pending"),
    TASK_CONFIRM_NOT_ALLOWED("3030", "task_confirm_not_allowed"),

    // notification
    NOTIFICATION_TEMPLATE_NOT_FOUND("4001", "notification_template_not_found"),
    PUSH_CHANNEL_UNAUTHORIZED("4010", "push_channel_unauthorized"),

    // workstation
    WORKSTATION_AGGREGATION_TIMEOUT("5001", "workstation_aggregation_timeout");

    private final String code;
    private final String slug;

    ErrorCode(String code, String slug) {
        this.code = code;
        this.slug = slug;
    }

    public String code() {
        return code;
    }

    public String slug() {
        return slug;
    }
}
