package com.apiece.springbootsnssample.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityConfigTest {

    private final SecurityConfig securityConfig = new SecurityConfig();

    @Test
    @DisplayName("PasswordEncoder는 Argon2PasswordEncoder(Argon2id)이다")
    void passwordEncoder_is_argon2() {
        // when
        PasswordEncoder passwordEncoder = securityConfig.passwordEncoder();

        // then
        assertThat(passwordEncoder).isInstanceOf(Argon2PasswordEncoder.class);
    }

    @Test
    @DisplayName("Argon2로 인코딩한 비밀번호는 matches로 검증된다")
    void argon2_encoded_password_matches() {
        // given
        PasswordEncoder passwordEncoder = securityConfig.passwordEncoder();

        // when
        String encoded = passwordEncoder.encode("password123");

        // then
        assertThat(encoded).startsWith("$argon2id$");
        assertThat(passwordEncoder.matches("password123", encoded)).isTrue();
        assertThat(passwordEncoder.matches("wrong-password", encoded)).isFalse();
    }
}
