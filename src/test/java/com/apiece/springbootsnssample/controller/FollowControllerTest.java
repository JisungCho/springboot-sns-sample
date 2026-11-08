package com.apiece.springbootsnssample.controller;

import com.apiece.springbootsnssample.config.SecurityConfig;
import com.apiece.springbootsnssample.domain.follow.Follow;
import com.apiece.springbootsnssample.domain.follow.FollowException;
import com.apiece.springbootsnssample.domain.follow.FollowService;
import com.apiece.springbootsnssample.domain.followcount.FollowCount;
import com.apiece.springbootsnssample.domain.followcount.FollowCountService;
import com.apiece.springbootsnssample.domain.user.User;
import com.apiece.springbootsnssample.domain.user.UserPrincipal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(FollowController.class)
@Import(SecurityConfig.class)
class FollowControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FollowService followService;

    @MockitoBean
    private FollowCountService followCountService;

    @MockitoBean
    private SessionRegistry sessionRegistry;

    @Test
    @DisplayName("팔로우 성공 시 201 Created와 Follow 정보를 반환한다")
    void follow_returns_201_created() throws Exception {
        // given
        given(followService.follow(1L, 2L))
                .willReturn(Follow.builder().id(10L).followerId(1L).followeeId(2L).build());

        // when & then
        mockMvc.perform(post("/api/v1/follow/2")
                        .with(user(principal(1L, "user1")))
                        .with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.followerId").value(1))
                .andExpect(jsonPath("$.followeeId").value(2));
    }

    @Test
    @DisplayName("언팔로우 성공 시 204 No Content를 반환한다")
    void unfollow_returns_204_no_content() throws Exception {
        // when & then
        mockMvc.perform(delete("/api/v1/follow/2")
                        .with(user(principal(1L, "user1")))
                        .with(csrf()))
                .andExpect(status().isNoContent());

        then(followService).should().unfollow(1L, 2L);
    }

    @Test
    @DisplayName("팔로워 목록 조회 시 200과 사용자 목록을 반환한다")
    void get_followers_returns_200() throws Exception {
        // given
        given(followService.getFollowers(1L)).willReturn(List.of(
                User.builder().id(2L).username("user2").password("encoded").build()));

        // when & then
        mockMvc.perform(get("/api/v1/followers")
                        .with(user(principal(1L, "user1"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(2))
                .andExpect(jsonPath("$[0].username").value("user2"));
    }

    @Test
    @DisplayName("팔로이 목록 조회 시 200과 사용자 목록을 반환한다")
    void get_followees_returns_200() throws Exception {
        // given
        given(followService.getFollowees(1L)).willReturn(List.of(
                User.builder().id(2L).username("user2").password("encoded").build()));

        // when & then
        mockMvc.perform(get("/api/v1/followees")
                        .with(user(principal(1L, "user1"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(2))
                .andExpect(jsonPath("$[0].username").value("user2"));
    }

    @Test
    @DisplayName("팔로우 카운트 조회 시 200과 followersCount, followeesCount를 반환한다")
    void get_follow_count_returns_200() throws Exception {
        // given
        given(followCountService.getByUserId(1L)).willReturn(
                FollowCount.builder()
                        .id(1L)
                        .userId(1L)
                        .followersCount(3L)
                        .followeesCount(5L)
                        .build());

        // when & then
        mockMvc.perform(get("/api/v1/follow-count")
                        .with(user(principal(1L, "user1"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.followersCount").value(3))
                .andExpect(jsonPath("$.followeesCount").value(5));
    }

    @Test
    @DisplayName("FollowException(CONFLICT)이 발생하면 409와 메시지를 반환한다")
    void follow_duplicate_returns_409() throws Exception {
        // given
        given(followService.follow(1L, 2L))
                .willThrow(new FollowException(HttpStatus.CONFLICT, "이미 팔로우한 사용자입니다."));

        // when & then
        mockMvc.perform(post("/api/v1/follow/2")
                        .with(user(principal(1L, "user1")))
                        .with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("이미 팔로우한 사용자입니다."));
    }

    @Test
    @DisplayName("비로그인 사용자가 팔로우 API에 접근하면 로그인 페이지로 리다이렉트된다")
    void follow_api_without_authentication_redirects_to_login() throws Exception {
        // when & then
        mockMvc.perform(get("/api/v1/follow-count"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    private static UserPrincipal principal(Long id, String username) {
        return new UserPrincipal(User.builder().id(id).username(username).password("encoded").build());
    }
}
