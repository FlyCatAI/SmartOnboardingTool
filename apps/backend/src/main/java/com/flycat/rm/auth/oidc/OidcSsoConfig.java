package com.flycat.rm.auth.oidc;

import com.flycat.rm.auth.SsoClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;

/**
 * 在 {@code sso.oidc.enabled=true} 时注册 {@link HttpSsoClient}。
 *
 * <p>prod profile 默认开启；dev / 测试可以通过 {@code SSO_OIDC_ENABLED=false}
 * 关闭以避免拉远端 SSO。
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(OidcProperties.class)
@ConditionalOnProperty(prefix = "sso.oidc", name = "enabled", havingValue = "true")
public class OidcSsoConfig {

    @Bean
    public SsoClient ssoClient(OidcProperties props) {
        props.validate();
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(props.getConnectTimeout())
                .build();
        ClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory = withReadTimeout(factory, (int) props.getReadTimeout().toMillis());
        RestClient.Builder builder = RestClient.builder().requestFactory(factory);
        return new HttpSsoClient(builder, props);
    }

    private static ClientHttpRequestFactory withReadTimeout(ClientHttpRequestFactory base, int readTimeoutMs) {
        if (base instanceof JdkClientHttpRequestFactory jdk) {
            jdk.setReadTimeout(readTimeoutMs);
            return jdk;
        }
        return base;
    }
}
