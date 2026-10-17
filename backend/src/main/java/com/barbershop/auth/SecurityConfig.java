package com.barbershop.auth;

import com.barbershop.common.config.AppProperties;
import com.barbershop.common.error.ProblemFactory;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Stateless API security:
 * <ul>
 *   <li>/api/public/** and the admin login are open (POSTs are rate-limited),</li>
 *   <li>/api/admin/** requires a valid admin JWT (role ADMIN),</li>
 *   <li>API docs and health are open, everything else is denied,</li>
 *   <li>CORS only for the configured frontend origin(s),</li>
 *   <li>401/403 are localized problem details like every other error.</li>
 * </ul>
 * No cookies/sessions → no CSRF surface.
 */
@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain apiSecurity(HttpSecurity http, ProblemFactory problems) throws Exception {
        AuthenticationEntryPoint unauthorized = (req, res, ex) ->
                problems.write(req, res, HttpStatus.UNAUTHORIZED, "auth.unauthorized");
        AccessDeniedHandler forbidden = (req, res, ex) ->
                problems.write(req, res, HttpStatus.FORBIDDEN, "auth.forbidden");

        http.csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/public/**", "/api/admin/auth/login").permitAll()
                        .requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers("/error").permitAll()
                        .requestMatchers("/api/admin/**").hasRole(AdminUser.ROLE_ADMIN)
                        .anyRequest().denyAll())
                .oauth2ResourceServer(rs -> rs
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(roleConverter()))
                        .authenticationEntryPoint(unauthorized)
                        .accessDeniedHandler(forbidden))
                .exceptionHandling(e -> e.authenticationEntryPoint(unauthorized).accessDeniedHandler(forbidden))
                .httpBasic(b -> b.disable())
                .formLogin(f -> f.disable());
        return http.build();
    }

    /** "role": "ADMIN" claim → ROLE_ADMIN authority. */
    private static JwtAuthenticationConverter roleConverter() {
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName(AdminPrincipal.ROLE_CLAIM);
        authorities.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return converter;
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(AppProperties props) {
        CorsConfiguration cors = new CorsConfiguration();
        cors.setAllowedOrigins(props.cors().allowedOrigins().stream().map(String::trim).filter(s -> !s.isEmpty()).toList());
        cors.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        cors.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept", "Accept-Language"));
        cors.setExposedHeaders(List.of("Location", "Retry-After", "Content-Language"));
        cors.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", cors);
        return source;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }
}
