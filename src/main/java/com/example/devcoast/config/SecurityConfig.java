
package com.example.devcoast.config;

import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.Customizer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.StaticHeadersWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    static final String CSP =
            "default-src 'self'; "
            + "script-src 'self' 'unsafe-inline' https://cdn.jsdelivr.net; "
            + "style-src 'self' 'unsafe-inline' https://fonts.googleapis.com; "
            + "font-src 'self' data: https://fonts.gstatic.com; "
            + "img-src 'self' data: blob: https:; "
            + "connect-src 'self'; "
            + "object-src 'none'; "
            + "base-uri 'self'; "
            + "form-action 'self'; "
            + "frame-ancestors 'none'";

    @Bean
    public BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public UrlBasedCorsConfigurationSource corsConfigurationSource() {

        CorsConfiguration config = new CorsConfiguration();

        // Flutter Web en desarrollo local
        config.setAllowedOriginPatterns(
                List.of(
                    "http://localhost:*",
                    "http://127.0.0.1:*"
                )
        );

        // Permitir cookies de sesión
        config.setAllowCredentials(true);

        config.setAllowedMethods(
                List.of(
                    "GET", "POST", "PUT",
                    "PATCH", "DELETE", "OPTIONS"
                )
        );

        config.setAllowedHeaders(
                List.of(
                    "Content-Type",
                    "Accept",
                    "X-CSRF-TOKEN",
                    "X-XSRF-TOKEN"
                )
        );

        UrlBasedCorsConfigurationSource source =
                new UrlBasedCorsConfigurationSource();

        source.registerCorsConfiguration(
                "/api/mobile/v1/**", config
        );

        return source;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http
    ) throws Exception {

        http
            .cors(Customizer.withDefaults())

            // Mantener CSRF habilitado
            .headers(h -> h
                .contentSecurityPolicy(
                    csp -> csp.policyDirectives(CSP)
                )
                .addHeaderWriter(
                    new StaticHeadersWriter(
                        "Referrer-Policy",
                        "strict-origin-when-cross-origin"
                    )
                )
                .addHeaderWriter(
                    new StaticHeadersWriter(
                        "Permissions-Policy",
                        "camera=(), microphone=(), "
                        + "geolocation=(), payment=(), usb=()"
                    )
                )
            )

            // Conserva temporalmente el comportamiento
            // de autorización de la aplicación original.
            .authorizeHttpRequests(
                auth -> auth.anyRequest().permitAll()
            )

            .formLogin(form -> form.disable())
            .httpBasic(basic -> basic.disable());

        return http.build();
    }
}
