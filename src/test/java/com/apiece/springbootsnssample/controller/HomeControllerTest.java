package com.apiece.springbootsnssample.controller;

import com.apiece.springbootsnssample.config.SecurityConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(HomeController.class)
@Import(SecurityConfig.class)
class HomeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SessionRegistry sessionRegistry;

    @Test
    @DisplayName("로그인한 사용자는 홈에서 인사말과 username을 본다")
    void home_returns_greeting_for_authenticated_user() throws Exception {
        // when & then
        mockMvc.perform(get("/").with(user("user1")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("user1")));
    }

    @Test
    @DisplayName("비로그인 사용자가 홈에 접근하면 로그인 페이지로 리다이렉트된다")
    void home_without_authentication_redirects_to_login() throws Exception {
        // when & then
        mockMvc.perform(get("/"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }
}