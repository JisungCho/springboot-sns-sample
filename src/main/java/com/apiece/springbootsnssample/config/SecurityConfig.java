package com.apiece.springbootsnssample.config;

import com.apiece.springbootsnssample.security.JsonAuthenticationFailureHandler;
import com.apiece.springbootsnssample.security.JsonAuthenticationSuccessHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import tools.jackson.databind.ObjectMapper;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    // OWASP 권장 Argon2id 파라미터 (m=19 MiB, t=2, p=1)
    private static final int SALT_LENGTH = 16;
    private static final int HASH_LENGTH = 32;
    private static final int PARALLELISM = 1;
    private static final int MEMORY_KB = 19456;
    private static final int ITERATIONS = 2;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new Argon2PasswordEncoder(SALT_LENGTH, HASH_LENGTH, PARALLELISM, MEMORY_KB, ITERATIONS);
    }

    @Bean
    public JsonAuthenticationSuccessHandler jsonAuthenticationSuccessHandler(ObjectMapper objectMapper) {
        return new JsonAuthenticationSuccessHandler(objectMapper);
    }

    @Bean
    public JsonAuthenticationFailureHandler jsonAuthenticationFailureHandler(ObjectMapper objectMapper) {
        return new JsonAuthenticationFailureHandler(objectMapper);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   JsonAuthenticationSuccessHandler successHandler,
                                                   JsonAuthenticationFailureHandler failureHandler) throws Exception {
        http
                .httpBasic(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/login", "/api/v1/users/signup").permitAll()
                        .anyRequest().authenticated()
                )
                .formLogin(form -> form
                        .loginProcessingUrl("/api/v1/login")
                        .successHandler(successHandler)
                        .failureHandler(failureHandler)
                );
        return http.build();
    }
}
