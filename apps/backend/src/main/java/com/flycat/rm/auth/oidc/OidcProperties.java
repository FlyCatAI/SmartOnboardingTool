package com.flycat.rm.auth.oidc;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;

/**
 * 行内 OIDC 桥接配置。
 *
 * <p>所有 endpoint、client_id、client_secret、redirect_uri 必须通过环境变量注入，
 * prod profile 不带任何默认值；缺一即拒绝启动（{@link #validate()}），保持 fail-closed。
 */
@ConfigurationProperties(prefix = "sso.oidc")
public class OidcProperties {

    private String tokenEndpoint;
    private String userinfoEndpoint;
    private String clientId;
    private String clientSecret;
    private String redirectUri;
    private Duration connectTimeout = Duration.ofSeconds(2);
    private Duration readTimeout = Duration.ofSeconds(5);
    private String roleClaim = "role";

    public void validate() {
        requireNonBlank(tokenEndpoint, "sso.oidc.token-endpoint");
        requireNonBlank(userinfoEndpoint, "sso.oidc.userinfo-endpoint");
        requireNonBlank(clientId, "sso.oidc.client-id");
        requireNonBlank(clientSecret, "sso.oidc.client-secret");
        requireNonBlank(redirectUri, "sso.oidc.redirect-uri");
        requireHttpsEndpoint(tokenEndpoint, "sso.oidc.token-endpoint");
        requireHttpsEndpoint(userinfoEndpoint, "sso.oidc.userinfo-endpoint");
    }

    private static void requireNonBlank(String value, String key) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalStateException(key + " is required (set the matching environment variable)");
        }
    }

    private static void requireHttpsEndpoint(String value, String key) {
        URI uri;
        try {
            uri = new URI(value.trim());
        } catch (URISyntaxException e) {
            throw new IllegalStateException(key + " must be a valid https:// URI", e);
        }
        if (!"https".equalsIgnoreCase(uri.getScheme())) {
            throw new IllegalStateException(key + " must use https://");
        }
    }

    public String getTokenEndpoint() {
        return tokenEndpoint;
    }

    public void setTokenEndpoint(String tokenEndpoint) {
        this.tokenEndpoint = tokenEndpoint;
    }

    public String getUserinfoEndpoint() {
        return userinfoEndpoint;
    }

    public void setUserinfoEndpoint(String userinfoEndpoint) {
        this.userinfoEndpoint = userinfoEndpoint;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getClientSecret() {
        return clientSecret;
    }

    public void setClientSecret(String clientSecret) {
        this.clientSecret = clientSecret;
    }

    public String getRedirectUri() {
        return redirectUri;
    }

    public void setRedirectUri(String redirectUri) {
        this.redirectUri = redirectUri;
    }

    public Duration getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(Duration connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public Duration getReadTimeout() {
        return readTimeout;
    }

    public void setReadTimeout(Duration readTimeout) {
        this.readTimeout = readTimeout;
    }

    public String getRoleClaim() {
        return roleClaim;
    }

    public void setRoleClaim(String roleClaim) {
        this.roleClaim = roleClaim;
    }
}
