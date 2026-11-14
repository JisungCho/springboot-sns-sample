package com.apiece.springbootsnssample.controller;

import com.apiece.springbootsnssample.config.SecurityConfig;
import com.apiece.springbootsnssample.domain.post.PostException;
import com.apiece.springbootsnssample.domain.post.PostViewCountService;
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
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PostViewController.class)
@Import(SecurityConfig.class)
class PostViewControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PostViewCountService postViewCountService;

    @MockitoBean
    private SessionRegistry sessionRegistry;

    @Test
    @DisplayName("조회수 증가 요청 시 204 No Content를 반환하고 서비스에 위임한다")
    void increase_view_count_returns_204() throws Exception {
        // when & then
        mockMvc.perform(post("/api/v1/posts/10/views")
                        .with(user("user1"))
                        .with(csrf()))
                .andExpect(status().isNoContent());

        then(postViewCountService).should().increaseViewCount(10L);
    }

    @Test
    @DisplayName("존재하지 않는 게시글이면 404 Not Found와 메시지를 반환한다")
    void increase_view_count_missing_post_returns_404() throws Exception {
        // given
        willThrow(new PostException(HttpStatus.NOT_FOUND, "존재하지 않는 게시글입니다."))
                .given(postViewCountService).increaseViewCount(anyLong());

        // when & then
        mockMvc.perform(post("/api/v1/posts/999/views")
                        .with(user("user1"))
                        .with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("존재하지 않는 게시글입니다."));
    }

    @Test
    @DisplayName("비로그인 사용자가 조회수 증가 API에 접근하면 로그인 페이지로 리다이렉트된다")
    void increase_view_count_without_authentication_redirects() throws Exception {
        // when & then
        mockMvc.perform(post("/api/v1/posts/10/views").with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));

        then(postViewCountService).shouldHaveNoInteractions();
    }
}
