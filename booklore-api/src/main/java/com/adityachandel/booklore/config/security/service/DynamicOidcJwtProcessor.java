package com.adityachandel.booklore.config.security.service;

import com.adityachandel.booklore.model.dto.settings.OidcProviderDetails;
import com.adityachandel.booklore.service.appsettings.AppSettingService;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.source.DefaultJWKSetCache;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.jwk.source.RemoteJWKSet;
import com.nimbusds.jose.proc.JWSKeySelector;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jose.util.DefaultResourceRetriever;
import com.nimbusds.jwt.proc.ConfigurableJWTProcessor;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class DynamicOidcJwtProcessor {
    private final AppSettingService appSettingService;

    private volatile ConfigurableJWTProcessor<SecurityContext> jwtProcessor;
    private volatile String currentIssuerUri;

    public synchronized ConfigurableJWTProcessor<SecurityContext> getProcessor() throws Exception {
        String issuerUri = appSettingService.getAppSettings().getOidcProviderDetails().getIssuerUri();
        if (jwtProcessor == null || !issuerUri.equals(currentIssuerUri)) {
            this.jwtProcessor = buildProcessor(issuerUri);
            this.currentIssuerUri = issuerUri;
        }
        return jwtProcessor;
    }

    private ConfigurableJWTProcessor<SecurityContext> buildProcessor(String issuerUri) throws Exception {
        OidcProviderDetails providerDetails = appSettingService.getAppSettings().getOidcProviderDetails();

        if (providerDetails == null || providerDetails.getIssuerUri() == null || providerDetails.getIssuerUri().isEmpty()) {
            throw new IllegalStateException("OIDC issuer URI is not configured in app settings.");
        }

        String discoveryUri = providerDetails.getIssuerUri().replaceAll("/$", "") + "/.well-known/openid-configuration";
        log.info("Fetching OIDC discovery document from {}", discoveryUri);

        URI jwksUri = fetchJwksUri(discoveryUri);

        Duration ttl = Duration.ofHours(6);
        Duration refresh = Duration.ofHours(1);

        DefaultResourceRetriever resourceRetriever = new DefaultResourceRetriever(10000, 10000);
        DefaultJWKSetCache jwkSetCache = new DefaultJWKSetCache(ttl.toMillis(), refresh.toMillis(), TimeUnit.MILLISECONDS);

        JWKSource<SecurityContext> jwkSource = new RemoteJWKSet<>(jwksUri.toURL(), resourceRetriever, jwkSetCache);

        Set<JWSAlgorithm> jwsAlgs = new HashSet<>();
        jwsAlgs.addAll(JWSAlgorithm.Family.RSA);
        jwsAlgs.addAll(JWSAlgorithm.Family.EC);
        jwsAlgs.addAll(JWSAlgorithm.Family.RSA);

        JWSKeySelector<SecurityContext> keySelector = new JWSVerificationKeySelector<>(jwsAlgs, jwkSource);
        ConfigurableJWTProcessor<SecurityContext> jwtProcessor = new DefaultJWTProcessor<>();
        jwtProcessor.setJWSKeySelector(keySelector);

        return jwtProcessor;
    }

    private URI fetchJwksUri(String discoveryUri) throws Exception {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10000);
        factory.setReadTimeout(10000);

        var restClient = org.springframework.web.client.RestClient.builder()
                .requestFactory(factory)
                .build();

        var discoveryDoc = restClient.get()
                .uri(discoveryUri)
                .retrieve()
                .body(new org.springframework.core.ParameterizedTypeReference<java.util.Map<String, Object>>() {});

        if (discoveryDoc == null) {
            throw new IllegalStateException("Failed to fetch OIDC discovery document.");
        }
        
        String jwksUriStr = (String) discoveryDoc.get("jwks_uri");
        if (jwksUriStr == null || jwksUriStr.isEmpty()) {
            throw new IllegalStateException("jwks_uri not found in OIDC discovery document.");
        }
        return new URI(jwksUriStr);
    }
}
