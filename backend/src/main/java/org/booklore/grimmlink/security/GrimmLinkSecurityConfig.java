package org.booklore.grimmlink.security;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletResponse;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.booklore.config.security.filter.AuthenticationCheckFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Slf4j
@Configuration
@AllArgsConstructor
public class GrimmLinkSecurityConfig {

    private final GrimmlinkAuthFilter grimmlinkAuthFilter;
    private final AuthenticationCheckFilter authenticationCheckFilter;

    @Bean
    @Order(0)
    public SecurityFilterChain grimmlinkSecurityChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher("/api/grimmlink/v1/**")
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .anyRequest().authenticated()
                )
                .addFilterBefore(grimmlinkAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(authenticationCheckFilter, UsernamePasswordAuthenticationFilter.class)
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, authException) -> {
                            log.debug("GrimmLink auth entrypoint: method={}, uri={}, dispatcherType={}",
                                    request.getMethod(), request.getRequestURI(), request.getDispatcherType().name());
                            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                            response.setContentType("application/json");
                            response.getWriter().write("{\"status\":\"error\",\"message\":\"Authentication required\"}");
                        })
                        .accessDeniedHandler((request, response, accessDeniedException) -> {
                            boolean authenticated = SecurityContextHolder.getContext().getAuthentication() != null
                                    && SecurityContextHolder.getContext().getAuthentication().isAuthenticated();
                            log.warn("GrimmLink access denied: method={}, uri={}, dispatcherType={}, authenticated={}",
                                    request.getMethod(), request.getRequestURI(), request.getDispatcherType().name(),
                                    authenticated);
                            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                            response.setContentType("application/json");
                            response.getWriter().write("{\"status\":\"error\",\"message\":\"Access denied\"}");
                        })
                );
        return http.build();
    }
}
