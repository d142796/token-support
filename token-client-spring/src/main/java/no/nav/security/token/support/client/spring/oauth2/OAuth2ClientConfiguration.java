package no.nav.security.token.support.client.spring.oauth2;

import no.nav.security.token.support.client.core.OAuth2CacheFactory;
import no.nav.security.token.support.client.core.context.JwtBearerTokenResolver;
import no.nav.security.token.support.client.core.http.OAuth2HttpClient;
import no.nav.security.token.support.client.core.oauth2.ClientCredentialsTokenClient;
import no.nav.security.token.support.client.core.oauth2.TokenExchangeClient;
import no.nav.security.token.support.client.core.oauth2.OAuth2AccessTokenService;
import no.nav.security.token.support.client.core.oauth2.OnBehalfOfTokenClient;
import no.nav.security.token.support.client.spring.ClientConfigurationProperties;
import no.nav.security.token.support.core.context.TokenValidationContextHolder;
import no.nav.security.token.support.core.jwt.JwtToken;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingClass;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportAware;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.type.AnnotationMetadata;

import java.util.Optional;

@EnableConfigurationProperties(ClientConfigurationProperties.class)
@Configuration
public class OAuth2ClientConfiguration implements ImportAware {

    private AnnotationAttributes enableOAuth2ClientAttributes;

    @Override
    public void setImportMetadata(AnnotationMetadata enableOAuth2ClientMetadata) {
        this.enableOAuth2ClientAttributes = AnnotationAttributes.fromMap(
            enableOAuth2ClientMetadata.getAnnotationAttributes(EnableOAuth2Client.class.getName(), false));
        if (this.enableOAuth2ClientAttributes == null) {
            throw new IllegalArgumentException(
                "@EnableOAuth2Client is not present on importing class " + enableOAuth2ClientMetadata.getClassName());
        }
    }

    @Bean
    OAuth2AccessTokenService oAuth2AccessTokenService(
        JwtBearerTokenResolver jwtBearerTokenResolver,
        OAuth2HttpClient oAuth2HttpClient
    ) {
        OAuth2AccessTokenService oAuth2AccessTokenService = new OAuth2AccessTokenService(
            jwtBearerTokenResolver,
            new OnBehalfOfTokenClient(oAuth2HttpClient),
            new ClientCredentialsTokenClient(oAuth2HttpClient),
            new TokenExchangeClient(oAuth2HttpClient));

        if (enableOAuth2ClientAttributes != null && enableOAuth2ClientAttributes.getBoolean("cacheEnabled")) {
            long maximumSize = enableOAuth2ClientAttributes.getNumber("cacheMaximumSize");
            long skewInSeconds = enableOAuth2ClientAttributes.getNumber("cacheEvictSkew");
            oAuth2AccessTokenService.setClientCredentialsGrantCache(
                OAuth2CacheFactory.accessTokenResponseCache(maximumSize, skewInSeconds));
            oAuth2AccessTokenService.setOnBehalfOfGrantCache(
                OAuth2CacheFactory.accessTokenResponseCache(maximumSize, skewInSeconds));
            oAuth2AccessTokenService.setExchangeGrantCache(
                OAuth2CacheFactory.accessTokenResponseCache(maximumSize, skewInSeconds));
        }
        return oAuth2AccessTokenService;
    }

    @Bean
    OAuth2HttpClient oAuth2HttpClient(RestTemplateBuilder restTemplateBuilder) {
        return new DefaultOAuth2HttpClient(restTemplateBuilder);
    }

    @Bean
    @ConditionalOnClass(TokenValidationContextHolder.class)
    JwtBearerTokenResolver jwtBearerTokenResolver(TokenValidationContextHolder contextHolder) {
        return () ->
            contextHolder.getTokenValidationContext() != null ?
                contextHolder.getTokenValidationContext().getFirstValidToken()
                    .map(JwtToken::getTokenAsString) : Optional.empty();
    }

    @Bean
    @ConditionalOnMissingBean(JwtBearerTokenResolver.class)
    @ConditionalOnMissingClass("no.nav.security.token.support.core.context.TokenValidationContextHolder")
    JwtBearerTokenResolver noopJwtBearerTokenResolver() {
        return () -> {
            throw new UnsupportedOperationException(
                String.format(
                    "a no-op implementation of %s is registered, cannot get token to exchange required for " +
                        "OnBehalfOf/TokenExchange grant",
                    JwtBearerTokenResolver.class
                )
            );
        };
    }
}
