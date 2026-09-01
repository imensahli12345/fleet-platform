package com.fleet.fleet.config;

import com.fleet.fleet.security.JwtAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(
                    "/v3/api-docs/**",
                    "/swagger-ui/**",
                    "/swagger-ui.html"
                ).permitAll()
                .requestMatchers(HttpMethod.PATCH, "/api/fleet/drivers/me").hasRole("DRIVER")
                .requestMatchers(HttpMethod.POST, "/api/fleet/drivers").hasRole("ADMIN")
                .requestMatchers(HttpMethod.PATCH, "/api/fleet/drivers/*/activation").hasRole("ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/api/fleet/drivers/*").hasRole("ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/fleet/trucks").hasRole("ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/fleet/trucks/*/assign").hasRole("ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/api/fleet/trucks/*").hasRole("ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/fleet/**").authenticated()
                .anyRequest().authenticated()
            )
            .httpBasic(AbstractHttpConfigurer::disable)
            .formLogin(AbstractHttpConfigurer::disable)
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
