package com.adityachandel.booklore.config.security;

import com.adityachandel.booklore.service.AppSettingService;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.jwk.source.RemoteJWKSet;
import com.nimbusds.jose.proc.JWSKeySelector;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jose.util.DefaultResourceRetriever;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.proc.ConfigurableJWTProcessor;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URL;
import java.util.Collections;
import java.util.Date;
import java.util.List;

@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 99)
@AllArgsConstructor
public class WebSocketAuthInterceptor implements ChannelInterceptor {

    private final JwtUtils jwtUtils;
    private final AppSettingService appSettingService;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
            List<String> authHeaders = accessor.getNativeHeader("Authorization");

            if (authHeaders == null || authHeaders.isEmpty()) {
                log.debug("WebSocket connection rejected: No Authorization header");
                throw new IllegalArgumentException("Missing Authorization header");
            }

            String token = authHeaders.get(0).replace("Bearer ", "");
            Authentication auth = authenticateToken(token);

            if (auth == null) {
                log.debug("WebSocket connection rejected: Invalid token");
                throw new IllegalArgumentException("Invalid Authorization token");
            }

            accessor.setUser(auth);
            log.debug("WebSocket authentication successful for user: {}", auth.getName());
        }

        return message;
    }

    private Authentication authenticateToken(String token) {
        if (jwtUtils.validateToken(token)) {
            String username = jwtUtils.extractUsername(token);
            List<SimpleGrantedAuthority> authorities = Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER"));
            return new UsernamePasswordAuthenticationToken(username, null, authorities);
        }

        if (appSettingService.getAppSettings().isOidcEnabled()) {
            try {
                var providerDetails = appSettingService.getAppSettings().getOidcProviderDetails();
                String jwksUrl = providerDetails.getJwksUrl();
                if (jwksUrl == null || jwksUrl.isEmpty()) {
                    log.error("JWKS URL is not configured");
                    return null;
                }

                URL jwksURL = new URI(jwksUrl).toURL();
                DefaultResourceRetriever resourceRetriever = new DefaultResourceRetriever(2000, 2000);
                JWKSource<SecurityContext> jwkSource = new RemoteJWKSet<>(jwksURL, resourceRetriever);
                ConfigurableJWTProcessor<SecurityContext> jwtProcessor = new DefaultJWTProcessor<>();
                JWSKeySelector<SecurityContext> keySelector = new JWSVerificationKeySelector<>(JWSAlgorithm.RS256, jwkSource);
                jwtProcessor.setJWSKeySelector(keySelector);

                JWTClaimsSet claimsSet = jwtProcessor.process(token, null);
                Date expirationTime = claimsSet.getExpirationTime();
                if (expirationTime == null || expirationTime.before(new Date())) {
                    log.warn("OIDC token is expired or missing exp claim");
                    return null;
                }
                return new UsernamePasswordAuthenticationToken("oidc-user", null, Collections.emptyList());

            } catch (Exception e) {
                log.error("OIDC token validation failed", e);
                return null;
            }
        }

        // If not OIDC-enabled, return null (or could throw an error depending on the requirement)
        return null;
    }
}