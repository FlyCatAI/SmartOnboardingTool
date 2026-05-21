package com.flycat.rm.performance.web;

import com.flycat.rm.common.error.BusinessException;
import com.flycat.rm.common.error.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalErrorHandlerTest {

    private final GlobalErrorHandler handler = new GlobalErrorHandler();

    @Test
    void sso_login_failed_maps_to_unauthorized() {
        ResponseEntity<ApiEnvelope<Void>> response =
                handler.handleBusiness(new BusinessException(ErrorCode.SSO_LOGIN_FAILED, "login failed"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().slug()).isEqualTo("sso_login_failed");
    }

    @Test
    void session_refresh_failed_maps_to_unauthorized() {
        ResponseEntity<ApiEnvelope<Void>> response =
                handler.handleBusiness(new BusinessException(ErrorCode.SESSION_REFRESH_FAILED, "refresh failed"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().slug()).isEqualTo("session_refresh_failed");
    }
}
