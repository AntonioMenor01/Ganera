package com.ganera.core.shared.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /** El frontend (Vite, otro origen/puerto) llama a esta API vía fetch/axios -- sin esto el
     * navegador bloquea la respuesta en el preflight antes de que la request llegue a ningun
     * controller, JWT valido o no. Solo el origen del frontend, no un wildcard. */
    @Bean
    public CorsConfigurationSource corsConfigurationSource(@Value("${ganera.frontend.origen}") String origenFrontend) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of(origenFrontend));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http, JwtService jwtService, CorsConfigurationSource corsConfigurationSource) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint((request, response, authException) ->
                                response.sendError(401)))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/webhooks/**").permitAll()
                        .requestMatchers("/internal/**").permitAll()
                        .requestMatchers("/auth/login").permitAll()
                        .requestMatchers("/gestorias/registro").permitAll()
                        // Decision 29: el "error dispatch" de Spring (cuerpo mal formado, id no
                        // numerico...) corre sin autenticacion -- JwtAuthenticationFilter es un
                        // OncePerRequestFilter y no se repite en el dispatch de error. Sin esto,
                        // un 400 acababa en 401 y el frontend cerraba la sesion. /error solo
                        // renderiza el estado/cuerpo de error ya decidido (sin mensaje ni traza,
                        // valores por defecto de Spring Boot); no hace publica ninguna otra ruta.
                        .requestMatchers("/error").permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(new JwtAuthenticationFilter(jwtService), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
