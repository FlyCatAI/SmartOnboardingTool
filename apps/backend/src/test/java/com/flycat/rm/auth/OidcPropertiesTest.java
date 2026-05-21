package com.flycat.rm.auth;

import com.flycat.rm.auth.oidc.OidcProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OidcPropertiesTest {

    @Test
    void validate_rejects_missing_token_endpoint() {
        OidcProperties p = baseProps();
        p.setTokenEndpoint("");
        assertThatThrownBy(p::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("sso.oidc.token-endpoint");
    }

    @Test
    void validate_rejects_missing_userinfo_endpoint() {
        OidcProperties p = baseProps();
        p.setUserinfoEndpoint(null);
        assertThatThrownBy(p::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("sso.oidc.userinfo-endpoint");
    }

    @Test
    void validate_rejects_missing_client_id() {
        OidcProperties p = baseProps();
        p.setClientId(" ");
        assertThatThrownBy(p::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("sso.oidc.client-id");
    }

    @Test
    void validate_rejects_missing_client_secret() {
        OidcProperties p = baseProps();
        p.setClientSecret(null);
        assertThatThrownBy(p::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("sso.oidc.client-secret");
    }

    @Test
    void validate_rejects_missing_redirect_uri() {
        OidcProperties p = baseProps();
        p.setRedirectUri("");
        assertThatThrownBy(p::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("sso.oidc.redirect-uri");
    }

    @Test
    void validate_rejects_plain_http_token_endpoint() {
        OidcProperties p = baseProps();
        p.setTokenEndpoint("http://sso/oidc/token");
        assertThatThrownBy(p::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("sso.oidc.token-endpoint")
                .hasMessageContaining("https://");
    }

    @Test
    void validate_rejects_plain_http_userinfo_endpoint() {
        OidcProperties p = baseProps();
        p.setUserinfoEndpoint("http://sso/oidc/userinfo");
        assertThatThrownBy(p::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("sso.oidc.userinfo-endpoint")
                .hasMessageContaining("https://");
    }

    @Test
    void validate_passes_when_all_required_fields_set() {
        OidcProperties p = baseProps();
        p.validate();
        assertThat(p.getTokenEndpoint()).isNotBlank();
    }

    private OidcProperties baseProps() {
        OidcProperties p = new OidcProperties();
        p.setTokenEndpoint("https://sso/oidc/token");
        p.setUserinfoEndpoint("https://sso/oidc/userinfo");
        p.setClientId("rm-miniapp");
        p.setClientSecret("s");
        p.setRedirectUri("https://miniapp/callback");
        return p;
    }
}
