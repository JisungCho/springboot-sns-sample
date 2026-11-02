package com.apiece.springbootsnssample.config;

import com.apiece.springbootsnssample.domain.user.User;
import com.apiece.springbootsnssample.domain.user.UserRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.http.MediaType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.RequestBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class LoginIntegrationTest {

    private static final String LOGIN_PROCESSING_URL = "/api/v1/login";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
        userRepository.save(new User("user1", passwordEncoder.encode("password123")));
    }

    private RequestBuilder login(String username, String password) {
        return formLogin().loginProcessingUrl(LOGIN_PROCESSING_URL).user(username).password(password);
    }

    @Test
    @DisplayName("올바른 username/password로 form 로그인하면 200과 username JSON을 응답한다")
    void form_login_success() throws Exception {
        // when & then
        mockMvc.perform(login("user1", "password123"))
                .andExpect(authenticated().withUsername("user1"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.username").value("user1"));
    }

    @Test
    @DisplayName("비밀번호가 틀리면 401과 에러 메시지 JSON을 응답한다")
    void form_login_with_wrong_password_fails() throws Exception {
        // when & then
        mockMvc.perform(login("user1", "wrong-password"))
                .andExpect(unauthenticated())
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("아이디 또는 비밀번호가 올바르지 않습니다."));
    }

    @Test
    @DisplayName("존재하지 않는 username이면 401과 에러 메시지 JSON을 응답한다")
    void form_login_with_unknown_username_fails() throws Exception {
        // when & then
        mockMvc.perform(login("ghost", "password123"))
                .andExpect(unauthenticated())
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("아이디 또는 비밀번호가 올바르지 않습니다."));
    }

    @Test
    @DisplayName("비로그인 사용자가 보호 페이지에 접근하면 로그인 페이지로 리다이렉트된다")
    void access_protected_page_without_authentication_redirects_to_login() throws Exception {
        // when & then
        mockMvc.perform(get("/"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    @DisplayName("로그인으로 받은 세션(SESSION)으로 보호 페이지에 접근할 수 있다")
    void access_protected_page_with_session() throws Exception {
        // given
        MvcResult loginResult = mockMvc.perform(login("user1", "password123"))
                .andExpect(authenticated())
                .andReturn();

        Cookie sessionCookie = loginResult.getResponse().getCookie("SESSION");
        assertThat(sessionCookie).isNotNull();

        // when & then
        mockMvc.perform(get("/").cookie(sessionCookie))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("user1")));
    }

    @Test
    @DisplayName("기본 로그인 페이지(GET /login)는 인증 없이 접근할 수 있다")
    void login_page_is_permitted() throws Exception {
        // when & then
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("로그아웃하면 인증이 해제되고 /login?logout으로 리다이렉트된다")
    void logout_success() throws Exception {
        // when & then
        mockMvc.perform(post("/logout").with(csrf()))
                .andExpect(unauthenticated())
                .andExpect(redirectedUrl("/login?logout"));
    }
}