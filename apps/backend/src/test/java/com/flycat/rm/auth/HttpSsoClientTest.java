package com.flycat.rm.auth;

import com.flycat.rm.auth.oidc.HttpSsoClient;
import com.flycat.rm.auth.oidc.OidcProperties;
import com.flycat.rm.common.error.BusinessException;
import com.flycat.rm.common.error.ErrorCode;
import com.flycat.rm.common.rbac.Principal;
import com.flycat.rm.common.rbac.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class HttpSsoClientTest {

    private static final String TOKEN_ENDPOINT = "https://sso.bank.example/oidc/token";
    private static final String USERINFO_ENDPOINT = "https://sso.bank.example/oidc/userinfo";

    private RestClient.Builder builder;
    private MockRestServiceServer server;
    private HttpSsoClient client;

    @BeforeEach
    void setUp() {
        builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new HttpSsoClient(builder, props());
    }

    private OidcProperties props() {
        OidcProperties p = new OidcProperties();
        p.setTokenEndpoint(TOKEN_ENDPOINT);
        p.setUserinfoEndpoint(USERINFO_ENDPOINT);
        p.setClientId("rm-miniapp");
        p.setClientSecret("secret-not-real");
        p.setRedirectUri("https://miniapp.bank.example/callback");
        p.setConnectTimeout(Duration.ofSeconds(2));
        p.setReadTimeout(Duration.ofSeconds(5));
        p.setRoleClaim("role");
        return p;
    }

    @Test
    void exchange_posts_authorization_code_to_token_endpoint_and_maps_userinfo_to_principal() {
        server.expect(requestTo(TOKEN_ENDPOINT))
                .andExpect(method(org.springframework.http.HttpMethod.POST))
                .andExpect(header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_FORM_URLENCODED_VALUE + ";charset=UTF-8"))
                .andExpect(content().formData(formParams("authorization_code", "abc123")))
                .andRespond(withSuccess(
                        "{\"access_token\":\"AT-xyz\",\"token_type\":\"Bearer\",\"expires_in\":28800,\"refresh_token\":\"RT-1\"}",
                        MediaType.APPLICATION_JSON));

        server.expect(requestTo(USERINFO_ENDPOINT))
                .andExpect(method(org.springframework.http.HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer AT-xyz"))
                .andRespond(withSuccess(
                        "{\"employee_id\":\"RM001\",\"name\":\"张三\",\"branch_id\":\"B-100\",\"team_id\":\"T-001\",\"role\":\"team_leader\"}",
                        MediaType.APPLICATION_JSON));

        Principal principal = client.exchange("abc123");

        assertThat(principal.employeeId()).isEqualTo("RM001");
        assertThat(principal.name()).isEqualTo("张三");
        assertThat(principal.branchId()).isEqualTo("B-100");
        assertThat(principal.teamId()).isEqualTo("T-001");
        assertThat(principal.role()).isEqualTo(Role.TEAM_LEADER);
        server.verify();
    }

    @Test
    void exchange_defaults_role_to_relationship_manager_when_claim_absent() {
        server.expect(requestTo(TOKEN_ENDPOINT))
                .andRespond(withSuccess(
                        "{\"access_token\":\"AT-1\",\"token_type\":\"Bearer\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(USERINFO_ENDPOINT))
                .andRespond(withSuccess(
                        "{\"employee_id\":\"RM777\",\"name\":\"李四\",\"branch_id\":\"B-200\",\"team_id\":\"T-200\"}",
                        MediaType.APPLICATION_JSON));

        Principal principal = client.exchange("code-default-role");

        assertThat(principal.role()).isEqualTo(Role.RELATIONSHIP_MANAGER);
    }

    @Test
    void exchange_rejects_blank_code_without_calling_sso() {
        assertThatThrownBy(() -> client.exchange("   "))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(ErrorCode.SSO_LOGIN_FAILED);
        server.verify();
    }

    @Test
    void exchange_maps_token_endpoint_4xx_to_sso_login_failed() {
        server.expect(requestTo(TOKEN_ENDPOINT))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":\"invalid_grant\"}"));

        assertThatThrownBy(() -> client.exchange("expired-code"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(ErrorCode.SSO_LOGIN_FAILED);
        server.verify();
    }

    @Test
    void exchange_maps_token_endpoint_5xx_to_sso_login_failed() {
        server.expect(requestTo(TOKEN_ENDPOINT))
                .andRespond(withStatus(HttpStatus.BAD_GATEWAY));

        assertThatThrownBy(() -> client.exchange("any-code"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(ErrorCode.SSO_LOGIN_FAILED);
        server.verify();
    }

    @Test
    void exchange_maps_missing_access_token_to_sso_login_failed() {
        server.expect(requestTo(TOKEN_ENDPOINT))
                .andRespond(withSuccess("{\"token_type\":\"Bearer\"}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.exchange("any-code"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(ErrorCode.SSO_LOGIN_FAILED);
        server.verify();
    }

    @Test
    void exchange_maps_userinfo_failure_to_sso_login_failed() {
        server.expect(requestTo(TOKEN_ENDPOINT))
                .andRespond(withSuccess(
                        "{\"access_token\":\"AT\",\"token_type\":\"Bearer\"}",
                        MediaType.APPLICATION_JSON));
        server.expect(requestTo(USERINFO_ENDPOINT))
                .andRespond(withStatus(HttpStatus.FORBIDDEN));

        assertThatThrownBy(() -> client.exchange("any-code"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(ErrorCode.SSO_LOGIN_FAILED);
        server.verify();
    }

    @Test
    void exchange_maps_missing_employee_id_to_sso_login_failed() {
        server.expect(requestTo(TOKEN_ENDPOINT))
                .andRespond(withSuccess(
                        "{\"access_token\":\"AT\",\"token_type\":\"Bearer\"}",
                        MediaType.APPLICATION_JSON));
        server.expect(requestTo(USERINFO_ENDPOINT))
                .andRespond(withSuccess(
                        "{\"name\":\"无工号\"}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.exchange("any-code"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(ErrorCode.SSO_LOGIN_FAILED);
    }

    @Test
    void refresh_posts_refresh_token_grant_and_returns_new_access_token() {
        server.expect(requestTo(TOKEN_ENDPOINT))
                .andExpect(method(org.springframework.http.HttpMethod.POST))
                .andExpect(content().formData(refreshParams("RT-old")))
                .andRespond(withSuccess(
                        "{\"access_token\":\"AT-new\",\"token_type\":\"Bearer\",\"expires_in\":28800}",
                        MediaType.APPLICATION_JSON));

        String fresh = client.refresh("RT-old");

        assertThat(fresh).isEqualTo("AT-new");
        server.verify();
    }

    @Test
    void refresh_rejects_blank_token_without_calling_sso() {
        assertThatThrownBy(() -> client.refresh(""))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(ErrorCode.SESSION_REFRESH_FAILED);
    }

    @Test
    void refresh_maps_invalid_grant_to_session_refresh_failed() {
        server.expect(requestTo(TOKEN_ENDPOINT))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":\"invalid_grant\"}"));

        assertThatThrownBy(() -> client.refresh("RT-expired"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(ErrorCode.SESSION_REFRESH_FAILED);
    }

    @Test
    void refresh_maps_missing_access_token_to_session_refresh_failed() {
        server.expect(requestTo(TOKEN_ENDPOINT))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.refresh("RT-1"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(ErrorCode.SESSION_REFRESH_FAILED);
    }

    private org.springframework.util.MultiValueMap<String, String> formParams(String grantType, String code) {
        org.springframework.util.LinkedMultiValueMap<String, String> form = new org.springframework.util.LinkedMultiValueMap<>();
        form.add("grant_type", grantType);
        form.add("code", code);
        form.add("client_id", "rm-miniapp");
        form.add("client_secret", "secret-not-real");
        form.add("redirect_uri", "https://miniapp.bank.example/callback");
        return form;
    }

    private org.springframework.util.MultiValueMap<String, String> refreshParams(String refreshToken) {
        org.springframework.util.LinkedMultiValueMap<String, String> form = new org.springframework.util.LinkedMultiValueMap<>();
        form.add("grant_type", "refresh_token");
        form.add("refresh_token", refreshToken);
        form.add("client_id", "rm-miniapp");
        form.add("client_secret", "secret-not-real");
        return form;
    }
}
