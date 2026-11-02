package com.apiece.springbootsnssample.config;

import com.apiece.springbootsnssample.domain.user.User;
import com.apiece.springbootsnssample.domain.user.UserRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** maximumSessions=1, maxSessionsPreventsLogin=false: 새 로그인 시 기존 세션이 만료된다. */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "app.session.maximum-sessions=1",
        "app.session.max-sessions-prevents-login=false"
})
class SessionPolicyExpireOldestTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @BeforeEach
    void setUp() {
        // 다른 테스트가 Redis에 남긴 세션이 동시 세션 수 집계에 영향을 주지 않도록 비운다.
        redisTemplate.delete(redisTemplate.keys("sns:session:*"));
        userRepository.deleteAll();
        userRepository.save(new User("user1", passwordEncoder.encode("password123")));
    }

    private Cookie loginAndGetSessionCookie() throws Exception {
        Cookie cookie = mockMvc.perform(formLogin("/api/v1/login").user("user1").password("password123"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getCookie("SESSION");
        assertThat(cookie).isNotNull();
        return cookie;
    }

    @Test
    @DisplayName("두 번째 로그인에 성공하면 첫 번째 세션은 만료되어 401을 받는다")
    void second_login_expires_first_session() throws Exception {
        // given
        Cookie first = loginAndGetSessionCookie();
        mockMvc.perform(get("/").cookie(first)).andExpect(status().isOk());

        // when
        Cookie second = loginAndGetSessionCookie();

        // then
        mockMvc.perform(get("/").cookie(first)).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/").cookie(second)).andExpect(status().isOk());
    }
}
