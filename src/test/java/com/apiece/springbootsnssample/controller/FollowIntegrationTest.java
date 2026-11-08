package com.apiece.springbootsnssample.controller;

import com.apiece.springbootsnssample.domain.follow.FollowRepository;
import com.apiece.springbootsnssample.domain.followcount.FollowCountRepository;
import com.apiece.springbootsnssample.domain.user.User;
import com.apiece.springbootsnssample.domain.user.UserRepository;
import com.apiece.springbootsnssample.domain.user.UserService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
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
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class FollowIntegrationTest {

    private static final String LOGIN_PROCESSING_URL = "/api/v1/login";
    private static final String RAW_PASSWORD = "password123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private FollowRepository followRepository;

    @Autowired
    private FollowCountRepository followCountRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private User user1;
    private User user2;

    @BeforeEach
    void setUp() {
        followRepository.deleteAll();
        followCountRepository.deleteAll();
        userRepository.deleteAll();

        user1 = userService.signup(new User("user1", RAW_PASSWORD));
        user2 = userService.signup(new User("user2", RAW_PASSWORD));
        assertThat(passwordEncoder.matches(RAW_PASSWORD, user1.getPassword())).isTrue();
    }

    private RequestBuilder login(String username) {
        return formLogin().loginProcessingUrl(LOGIN_PROCESSING_URL).user(username).password(RAW_PASSWORD);
    }

    private Cookie sessionOf(String username) throws Exception {
        MvcResult loginResult = mockMvc.perform(login(username))
                .andExpect(status().isOk())
                .andReturn();
        Cookie sessionCookie = loginResult.getResponse().getCookie("SESSION");
        assertThat(sessionCookie).isNotNull();
        return sessionCookie;
    }

    @Test
    @DisplayName("팔로우/언팔로우 흐름: 목록·카운트 반영, 중복 409, 자기자신 400, 재언팔로우 404")
    void follow_unfollow_flow() throws Exception {
        // given
        Cookie user1Session = sessionOf("user1");
        Cookie user2Session = sessionOf("user2");

        // when & then
        mockMvc.perform(post("/api/v1/follow/" + user2.getId())
                        .cookie(user1Session)
                        .with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.followerId").value(user1.getId()))
                .andExpect(jsonPath("$.followeeId").value(user2.getId()));

        mockMvc.perform(get("/api/v1/followees").cookie(user1Session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(user2.getId()))
                .andExpect(jsonPath("$[0].username").value("user2"));

        mockMvc.perform(get("/api/v1/followers").cookie(user2Session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(user1.getId()))
                .andExpect(jsonPath("$[0].username").value("user1"));

        mockMvc.perform(get("/api/v1/follow-count").cookie(user1Session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.followersCount").value(0))
                .andExpect(jsonPath("$.followeesCount").value(1));

        mockMvc.perform(get("/api/v1/follow-count").cookie(user2Session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.followersCount").value(1))
                .andExpect(jsonPath("$.followeesCount").value(0));

        mockMvc.perform(post("/api/v1/follow/" + user2.getId())
                        .cookie(user1Session)
                        .with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("이미 팔로우한 사용자입니다."));

        mockMvc.perform(post("/api/v1/follow/" + user1.getId())
                        .cookie(user1Session)
                        .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("자기 자신을 팔로우할 수 없습니다."));

        mockMvc.perform(delete("/api/v1/follow/" + user2.getId())
                        .cookie(user1Session)
                        .with(csrf()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/follow-count").cookie(user1Session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.followersCount").value(0))
                .andExpect(jsonPath("$.followeesCount").value(0));

        mockMvc.perform(get("/api/v1/follow-count").cookie(user2Session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.followersCount").value(0))
                .andExpect(jsonPath("$.followeesCount").value(0));

        mockMvc.perform(delete("/api/v1/follow/" + user2.getId())
                        .cookie(user1Session)
                        .with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("팔로우 관계가 존재하지 않습니다."));
    }
}
