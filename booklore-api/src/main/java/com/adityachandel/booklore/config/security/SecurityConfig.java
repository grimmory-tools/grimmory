package com.adityachandel.booklore.config.security;

import com.adityachandel.booklore.config.AppProperties;
import com.adityachandel.booklore.config.security.filter.CoverJwtFilter;
import com.adityachandel.booklore.config.security.filter.DualJwtAuthenticationFilter;
import com.adityachandel.booklore.config.security.filter.KoboAuthFilter;
import com.adityachandel.booklore.config.security.filter.KoreaderAuthFilter;
import com.adityachandel.booklore.config.security.service.OpdsUserDetailsService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.AllArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@AllArgsConstructor
@EnableMethodSecurity
@Configuration
public class SecurityConfig {

    private final OpdsUserDetailsService opdsUserDetailsService;
    private final DualJwtAuthenticationFilter dualJwtAuthenticationFilter;
    private final AppProperties appProperties;

    private static final String[] SWAGGER_ENDPOINTS = {
            "/api/v1/swagger-ui.html",
            "/api/v1/swagger-ui/**",
            "/api/v1/api-docs/**"
    };

    private static final String[] COMMON_PUBLIC_ENDPOINTS = {
            "/ws/**",                  // WebSocket connections (auth handled in WebSocketAuthInterceptor)
            "/kobo/**",                // Kobo API requests (auth handled in KoboAuthFilter)
            "/api/v1/auth/**",         // Login and token refresh endpoints (must remain public)
            "/api/v1/public-settings", // Public endpoint for checking OIDC or other app settings
            "/api/v1/setup/**",        // Setup wizard endpoints (must remain accessible before initial setup)
            "/api/v1/healthcheck/**"   // Healthcheck endpoints (must remain accessible for Docker healthchecks)
    };

    private static final String[] COMMON_UNAUTHENTICATED_ENDPOINTS = {
            "/api/v1/opds/search.opds",
            "/api/v2/opds/search.opds"
    };

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    @Order(1)
    public SecurityFilterChain opdsBasicAuthSecurityChain(HttpSecurity http) throws Exception {
        List<String> unauthenticatedEndpoints = new ArrayList<>(Arrays.asList(COMMON_UNAUTHENTICATED_ENDPOINTS));
        http
                .securityMatcher("/api/v1/opds/**", "/api/v2/opds/**")
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(unauthenticatedEndpoints.toArray(new String[0])).permitAll()
                        .anyRequest().authenticated()
                )
                .httpBasic(basic -> basic
                        .realmName("Booklore OPDS")
                        .authenticationEntryPoint((request, response, authException) -> {
                            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                            response.setHeader("WWW-Authenticate", "Basic realm=\"Booklore OPDS\"");
                            response.getWriter().write("HTTP Status 401 - " + authException.getMessage());
                        })
                );

        return http.build();
    }

    @Bean
    @Order(2)
    public SecurityFilterChain koreaderSecurityChain(HttpSecurity http, KoreaderAuthFilter koreaderAuthFilter) throws Exception {
        http
                .securityMatcher("/api/koreader/**")
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                .addFilterBefore(koreaderAuthFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    @Order(3)
    public SecurityFilterChain koboSecurityChain(HttpSecurity http, KoboAuthFilter koboAuthFilter) throws Exception {
        http
                .securityMatcher("/api/kobo/**")
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                .addFilterBefore(koboAuthFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    @Order(4)
    public SecurityFilterChain coverJwtApiSecurityChain(HttpSecurity http, CoverJwtFilter coverJwtFilter) throws Exception {
        http
                .securityMatcher("/api/v1/media/**")
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .headers(headers -> headers
                        .cacheControl(HeadersConfigurer.CacheControlConfig::disable)
                )
                .authorizeHttpRequests(auth -> auth
                        .anyRequest().permitAll()
                )
                .addFilterBefore(coverJwtFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(dualJwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    @Order(5)
    public SecurityFilterChain jwtApiSecurityChain(HttpSecurity http) throws Exception {
        List<String> publicEndpoints = new ArrayList<>(Arrays.asList(COMMON_PUBLIC_ENDPOINTS));
        if (appProperties.getSwagger().isEnabled()) {
            publicEndpoints.addAll(Arrays.asList(SWAGGER_ENDPOINTS));
        }
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(publicEndpoints.toArray(new String[0])).permitAll()
                        .anyRequest().authenticated()
                )
                .addFilterBefore(dualJwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public AuthenticationManager authenticationManager(HttpSecurity http) throws Exception {
        // Configure the shared AuthenticationManagerBuilder with the UserDetailsService and PasswordEncoder
        AuthenticationManagerBuilder auth = http.getSharedObject(AuthenticationManagerBuilder.class);
        auth.userDetailsService(opdsUserDetailsService).passwordEncoder(passwordEncoder());
        return auth.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(List.of("*"));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        configuration.setAllowedHeaders(List.of("Authorization", "Cache-Control", "Content-Type"));
        configuration.setExposedHeaders(List.of("Content-Disposition"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);

        return source;
    }
}
