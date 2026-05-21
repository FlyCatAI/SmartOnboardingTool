package com.flycat.rm.auth;

import com.flycat.rm.auth.oidc.OidcSsoConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class OidcSsoConfigTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(OidcSsoConfig.class)
            .withPropertyValues(
                    "sso.oidc.enabled=true",
                    "sso.oidc.token-endpoint=https://sso/oidc/token",
                    "sso.oidc.userinfo-endpoint=https://sso/oidc/userinfo",
                    "sso.oidc.client-id=rm-miniapp",
                    "sso.oidc.client-secret=secret-not-real",
                    "sso.oidc.redirect-uri=https://miniapp/callback");

    @Test
    void startup_fails_when_token_endpoint_is_not_https() {
        contextRunner
                .withPropertyValues("sso.oidc.token-endpoint=http://sso/oidc/token")
                .run(context -> assertThat(rootCause(context.getStartupFailure()))
                        .hasMessageContaining("sso.oidc.token-endpoint")
                        .hasMessageContaining("https://"));
    }

    @Test
    void startup_fails_when_userinfo_endpoint_is_not_https() {
        contextRunner
                .withPropertyValues("sso.oidc.userinfo-endpoint=http://sso/oidc/userinfo")
                .run(context -> assertThat(rootCause(context.getStartupFailure()))
                        .hasMessageContaining("sso.oidc.userinfo-endpoint")
                        .hasMessageContaining("https://"));
    }

    private static Throwable rootCause(Throwable throwable) {
        assertThat(throwable).isNotNull();
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }
}
