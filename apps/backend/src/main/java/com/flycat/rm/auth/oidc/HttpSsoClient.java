package com.flycat.rm.auth.oidc;

import com.flycat.rm.auth.SsoClient;
import com.flycat.rm.common.error.BusinessException;
import com.flycat.rm.common.error.ErrorCode;
import com.flycat.rm.common.rbac.Principal;
import com.flycat.rm.common.rbac.Role;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Locale;

/**
 * 生产 {@link SsoClient} 实现：按 OIDC authorization_code 流程
 * 用一次性 code 换 access_token，然后用 access_token 拉 userinfo claims
 * 映射成 {@link Principal}。
 *
 * <p>所有失败路径都映射成 {@link BusinessException}：exchange 失败 →
 * {@link ErrorCode#SSO_LOGIN_FAILED}，refresh 失败 →
 * {@link ErrorCode#SESSION_REFRESH_FAILED}。原始异常和敏感字段不外泄。
 */
public class HttpSsoClient implements SsoClient {

    private final RestClient http;
    private final OidcProperties props;

    public HttpSsoClient(RestClient.Builder builder, OidcProperties props) {
        this.props = props;
        this.http = builder.build();
    }

    @Override
    public Principal exchange(String code) {
        if (isBlank(code)) {
            throw new BusinessException(ErrorCode.SSO_LOGIN_FAILED, "authorization code is blank");
        }
        JsonNode tokenResponse = postTokenEndpoint(authorizationCodeForm(code), ErrorCode.SSO_LOGIN_FAILED);
        String accessToken = readText(tokenResponse, "access_token");
        if (isBlank(accessToken)) {
            throw new BusinessException(ErrorCode.SSO_LOGIN_FAILED, "token endpoint did not return access_token");
        }
        JsonNode userinfo = fetchUserinfo(accessToken);
        return mapPrincipal(userinfo);
    }

    @Override
    public String refresh(String sessionToken) {
        if (isBlank(sessionToken)) {
            throw new BusinessException(ErrorCode.SESSION_REFRESH_FAILED, "session token is blank");
        }
        JsonNode tokenResponse = postTokenEndpoint(refreshTokenForm(sessionToken), ErrorCode.SESSION_REFRESH_FAILED);
        String accessToken = readText(tokenResponse, "access_token");
        if (isBlank(accessToken)) {
            throw new BusinessException(ErrorCode.SESSION_REFRESH_FAILED, "token endpoint did not return access_token");
        }
        return accessToken;
    }

    private JsonNode postTokenEndpoint(MultiValueMap<String, String> form, ErrorCode failureCode) {
        try {
            JsonNode body = http.post()
                    .uri(props.getTokenEndpoint())
                    .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(JsonNode.class);
            if (body == null) {
                throw new BusinessException(failureCode, "token endpoint returned empty body");
            }
            return body;
        } catch (RestClientException e) {
            throw new BusinessException(failureCode, "token endpoint call failed", e);
        }
    }

    private JsonNode fetchUserinfo(String accessToken) {
        try {
            JsonNode body = http.get()
                    .uri(props.getUserinfoEndpoint())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                    .retrieve()
                    .body(JsonNode.class);
            if (body == null) {
                throw new BusinessException(ErrorCode.SSO_LOGIN_FAILED, "userinfo endpoint returned empty body");
            }
            return body;
        } catch (RestClientException e) {
            throw new BusinessException(ErrorCode.SSO_LOGIN_FAILED, "userinfo endpoint call failed", e);
        }
    }

    private Principal mapPrincipal(JsonNode userinfo) {
        String employeeId = readText(userinfo, "employee_id");
        if (isBlank(employeeId)) {
            throw new BusinessException(ErrorCode.SSO_LOGIN_FAILED, "userinfo missing employee_id");
        }
        String name = firstNonBlank(readText(userinfo, "name"), employeeId);
        String branchId = readText(userinfo, "branch_id");
        String teamId = readText(userinfo, "team_id");
        Role role = parseRole(readText(userinfo, props.getRoleClaim()));
        return new Principal(employeeId, name, branchId, teamId, role);
    }

    private MultiValueMap<String, String> authorizationCodeForm(String code) {
        LinkedMultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("code", code);
        form.add("client_id", props.getClientId());
        form.add("client_secret", props.getClientSecret());
        form.add("redirect_uri", props.getRedirectUri());
        return form;
    }

    private MultiValueMap<String, String> refreshTokenForm(String refreshToken) {
        LinkedMultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "refresh_token");
        form.add("refresh_token", refreshToken);
        form.add("client_id", props.getClientId());
        form.add("client_secret", props.getClientSecret());
        return form;
    }

    private static Role parseRole(String raw) {
        if (isBlank(raw)) {
            return Role.RELATIONSHIP_MANAGER;
        }
        try {
            return Role.valueOf(raw.trim().toUpperCase(Locale.ROOT).replace('-', '_'));
        } catch (IllegalArgumentException e) {
            return Role.RELATIONSHIP_MANAGER;
        }
    }

    private static String readText(JsonNode node, String field) {
        if (node == null || field == null) {
            return null;
        }
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        return value.asText();
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static String firstNonBlank(String a, String b) {
        return isBlank(a) ? b : a;
    }
}
