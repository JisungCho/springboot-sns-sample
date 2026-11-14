package com.apiece.springbootsnssample.controller;

import com.apiece.springbootsnssample.config.SecurityConfig;
import com.apiece.springbootsnssample.domain.like.LikeException;
import com.apiece.springbootsnssample.domain.like.LikeResult;
import com.apiece.springbootsnssample.domain.like.LikeService;
import com.apiece.springbootsnssample.domain.post.Post;
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

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(LikeController.class)
@Import(SecurityConfig.class)
class LikeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private LikeService likeService;

    @MockitoBean
    private SessionRegistry sessionRegistry;

    @Test
    @DisplayName("좋아요 시 201 Created와 함께 게시글의 likeCount를 반환한다")
    void like_returns_201_with_like_count() throws Exception {
        // given
        given(likeService.like(2L, 10L))
                .willReturn(new LikeResult(100L, 2L, 10L, 1));

        // when & then
        mockMvc.perform(post("/api/v1/posts/10/likes")
                        .with(user(principal(2L, "user2")))
                        .with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(100))
                .andExpect(jsonPath("$.userId").value(2))
                .andExpect(jsonPath("$.postId").value(10))
                .andExpect(jsonPath("$.likeCount").value(1));
    }

    @Test
    @DisplayName("중복 좋아요 시 409 Conflict를 반환한다")
    void like_duplicate_returns_409() throws Exception {
        // given
        willThrow(new LikeException(HttpStatus.CONFLICT, "이미 좋아요한 게시글입니다."))
                .given(likeService).like(anyLong(), anyLong());

        // when & then
        mockMvc.perform(post("/api/v1/posts/10/likes")
                        .with(user(principal(2L, "user2")))
                        .with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("이미 좋아요한 게시글입니다."));
    }

    @Test
    @DisplayName("존재하지 않는 게시글에 좋아요 시 404 Not Found를 반환한다")
    void like_missing_post_returns_404() throws Exception {
        // given
        willThrow(new LikeException(HttpStatus.NOT_FOUND, "존재하지 않는 게시글입니다."))
                .given(likeService).like(anyLong(), anyLong());

        // when & then
        mockMvc.perform(post("/api/v1/posts/999/likes")
                        .with(user(principal(2L, "user2")))
                        .with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("존재하지 않는 게시글입니다."));
    }

    @Test
    @DisplayName("좋아요 취소 시 204 No Content를 반환한다")
    void unlike_returns_204() throws Exception {
        // when & then
        mockMvc.perform(delete("/api/v1/posts/10/likes")
                        .with(user(principal(2L, "user2")))
                        .with(csrf()))
                .andExpect(status().isNoContent());

        then(likeService).should().unlike(2L, 10L);
    }

    @Test
    @DisplayName("좋아요하지 않은 글 취소 시 404 Not Found를 반환한다")
    void unlike_missing_returns_404() throws Exception {
        // given
        willThrow(new LikeException(HttpStatus.NOT_FOUND, "좋아요한 게시글이 아닙니다."))
                .given(likeService).unlike(anyLong(), anyLong());

        // when & then
        mockMvc.perform(delete("/api/v1/posts/10/likes")
                        .with(user(principal(2L, "user2")))
                        .with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("좋아요한 게시글이 아닙니다."));
    }

    @Test
    @DisplayName("좋아요한 게시글 목록 조회 시 200과 목록을 반환한다")
    void get_liked_posts_returns_200() throws Exception {
        // given
        given(likeService.getLikedPosts(2L)).willReturn(List.of(
                Post.builder().id(11L).user(authorUser(1L, "user1")).content("second").likeCount(2).build(),
                Post.builder().id(10L).user(authorUser(1L, "user1")).content("first").likeCount(1).build()));

        // when & then
        mockMvc.perform(get("/api/v1/liked-posts")
                        .with(user(principal(2L, "user2"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(11))
                .andExpect(jsonPath("$[0].likeCount").value(2))
                .andExpect(jsonPath("$[1].id").value(10));
    }

    @Test
    @DisplayName("비로그인 사용자가 좋아요 API에 접근하면 로그인 페이지로 리다이렉트된다")
    void like_api_without_authentication_redirects_to_login() throws Exception {
        // when & then
        mockMvc.perform(post("/api/v1/posts/10/likes").with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));

        then(likeService).shouldHaveNoInteractions();
    }

    private static User authorUser(Long id, String username) {
        return User.builder().id(id).username(username).password("encoded").build();
    }

    private static UserPrincipal principal(Long id, String username) {
        return new UserPrincipal(authorUser(id, username));
    }
}
