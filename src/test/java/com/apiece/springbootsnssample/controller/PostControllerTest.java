package com.apiece.springbootsnssample.controller;

import com.apiece.springbootsnssample.config.SecurityConfig;
import com.apiece.springbootsnssample.domain.post.Post;
import com.apiece.springbootsnssample.domain.post.PostException;
import com.apiece.springbootsnssample.domain.post.PostService;
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
import static org.mockito.ArgumentMatchers.eq;
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

@WebMvcTest(PostController.class)
@Import(SecurityConfig.class)
class PostControllerTest {

    private static final String CONTENT_TYPE = "application/json";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PostService postService;

    @MockitoBean
    private SessionRegistry sessionRegistry;

    @Test
    @DisplayName("게시글 작성 시 201 Created와 게시글 정보를 반환한다")
    void create_post_returns_201() throws Exception {
        // given
        given(postService.createPost(1L, "content"))
                .willReturn(Post.builder().id(10L).user(authorUser(1L, "user1")).content("content").build());

        // when & then
        mockMvc.perform(post("/api/v1/posts")
                        .with(user(principal(1L, "user1")))
                        .with(csrf())
                        .contentType(CONTENT_TYPE)
                        .content("{\"content\":\"content\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.content").value("content"))
                .andExpect(jsonPath("$.authorId").value(1))
                .andExpect(jsonPath("$.authorUsername").value("user1"));
    }

    @Test
    @DisplayName("본문이 공백이면 400 Bad Request와 검증 메시지를 반환한다")
    void create_post_blank_content_returns_400() throws Exception {
        // when & then
        mockMvc.perform(post("/api/v1/posts")
                        .with(user(principal(1L, "user1")))
                        .with(csrf())
                        .contentType(CONTENT_TYPE)
                        .content("{\"content\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("본문은 필수입니다."));

        then(postService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("본문이 500자를 넘으면 400 Bad Request를 반환한다")
    void create_post_too_long_content_returns_400() throws Exception {
        // given
        String content = "a".repeat(Post.MAX_CONTENT_LENGTH + 1);

        // when & then
        mockMvc.perform(post("/api/v1/posts")
                        .with(user(principal(1L, "user1")))
                        .with(csrf())
                        .contentType(CONTENT_TYPE)
                        .content("{\"content\":\"" + content + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("본문은 500자 이하로 입력해주세요."));
    }

    @Test
    @DisplayName("게시글 단건 조회 시 200과 카운트를 반환한다")
    void get_post_returns_200() throws Exception {
        // given
        Post post = Post.builder()
                .id(10L)
                .user(authorUser(1L, "user1"))
                .content("content")
                .viewCount(3)
                .build();
        given(postService.getPost(10L)).willReturn(post);

        // when & then
        mockMvc.perform(get("/api/v1/posts/10")
                        .with(user(principal(1L, "user1"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.viewCount").value(3))
                .andExpect(jsonPath("$.replyCount").value(0));
    }

    @Test
    @DisplayName("존재하지 않는 게시글 조회 시 404 Not Found를 반환한다")
    void get_missing_post_returns_404() throws Exception {
        // given
        given(postService.getPost(999L))
                .willThrow(new PostException(HttpStatus.NOT_FOUND, "존재하지 않는 게시글입니다."));

        // when & then
        mockMvc.perform(get("/api/v1/posts/999")
                        .with(user(principal(1L, "user1"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("존재하지 않는 게시글입니다."));
    }

    @Test
    @DisplayName("게시글 삭제 시 204 No Content를 반환한다")
    void delete_post_returns_204() throws Exception {
        // when & then
        mockMvc.perform(delete("/api/v1/posts/10")
                        .with(user(principal(1L, "user1")))
                        .with(csrf()))
                .andExpect(status().isNoContent());

        then(postService).should().deletePost(1L, 10L);
    }

    @Test
    @DisplayName("본인 글이 아닌 게시글 삭제 시 403 Forbidden을 반환한다")
    void delete_others_post_returns_403() throws Exception {
        // given
        willThrow(new PostException(HttpStatus.FORBIDDEN, "본인이 작성한 게시글만 삭제할 수 있습니다."))
                .given(postService).deletePost(anyLong(), anyLong());

        // when & then
        mockMvc.perform(delete("/api/v1/posts/10")
                        .with(user(principal(2L, "user2")))
                        .with(csrf()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("본인이 작성한 게시글만 삭제할 수 있습니다."));
    }

    @Test
    @DisplayName("답글 작성 시 201과 parentId가 담긴 응답을 반환한다")
    void create_reply_returns_201() throws Exception {
        // given
        Post reply = Post.builder().id(11L).user(authorUser(2L, "user2")).content("reply").parentId(10L).build();
        given(postService.createReply(2L, 10L, "reply")).willReturn(reply);

        // when & then
        mockMvc.perform(post("/api/v1/posts/10/replies")
                        .with(user(principal(2L, "user2")))
                        .with(csrf())
                        .contentType(CONTENT_TYPE)
                        .content("{\"content\":\"reply\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(11))
                .andExpect(jsonPath("$.parentId").value(10));
    }

    @Test
    @DisplayName("답글 목록 조회 시 200과 목록을 반환한다")
    void get_replies_returns_200() throws Exception {
        // given
        given(postService.getReplies(10L)).willReturn(List.of(
                Post.builder().id(11L).user(authorUser(2L, "user2")).content("r1").parentId(10L).build(),
                Post.builder().id(12L).user(authorUser(2L, "user2")).content("r2").parentId(10L).build()));

        // when & then
        mockMvc.perform(get("/api/v1/posts/10/replies")
                        .with(user(principal(1L, "user1"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].content").value("r1"))
                .andExpect(jsonPath("$[1].content").value("r2"));
    }

    @Test
    @DisplayName("인용글 작성 시 201과 quoteId가 담긴 응답을 반환한다")
    void create_quote_returns_201() throws Exception {
        // given
        Post quote = Post.builder().id(13L).user(authorUser(2L, "user2")).content("quote").quoteId(10L).build();
        given(postService.createQuote(2L, 10L, "quote")).willReturn(quote);

        // when & then
        mockMvc.perform(post("/api/v1/posts/10/quotes")
                        .with(user(principal(2L, "user2")))
                        .with(csrf())
                        .contentType(CONTENT_TYPE)
                        .content("{\"content\":\"quote\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.quoteId").value(10));
    }

    @Test
    @DisplayName("인용한 글 목록 조회 시 200과 목록을 반환한다")
    void get_quotes_returns_200() throws Exception {
        // given
        given(postService.getQuotes(10L)).willReturn(List.of(
                Post.builder().id(13L).user(authorUser(2L, "user2")).content("quote").quoteId(10L).build()));

        // when & then
        mockMvc.perform(get("/api/v1/posts/10/quotes")
                        .with(user(principal(1L, "user1"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].quoteId").value(10));
    }

    @Test
    @DisplayName("리포스트 시 201과 repostId만 담긴 응답을 반환한다")
    void create_repost_returns_201() throws Exception {
        // given
        Post repost = Post.builder().id(14L).user(authorUser(2L, "user2")).repostId(10L).build();
        given(postService.createRepost(2L, 10L)).willReturn(repost);

        // when & then
        mockMvc.perform(post("/api/v1/posts/10/reposts")
                        .with(user(principal(2L, "user2")))
                        .with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.repostId").value(10))
                .andExpect(jsonPath("$.content").doesNotExist());
    }

    @Test
    @DisplayName("중복 리포스트 시 409 Conflict를 반환한다")
    void create_repost_duplicate_returns_409() throws Exception {
        // given
        willThrow(new PostException(HttpStatus.CONFLICT, "이미 리포스트한 게시글입니다."))
                .given(postService).createRepost(anyLong(), anyLong());

        // when & then
        mockMvc.perform(post("/api/v1/posts/10/reposts")
                        .with(user(principal(2L, "user2")))
                        .with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("이미 리포스트한 게시글입니다."));
    }

    @Test
    @DisplayName("리포스트 취소 시 204 No Content를 반환한다")
    void cancel_repost_returns_204() throws Exception {
        // when & then
        mockMvc.perform(delete("/api/v1/posts/10/reposts")
                        .with(user(principal(2L, "user2")))
                        .with(csrf()))
                .andExpect(status().isNoContent());

        then(postService).should().cancelRepost(2L, 10L);
    }

    @Test
    @DisplayName("리포스트한 글 목록 조회 시 200과 목록을 반환한다")
    void get_reposts_returns_200() throws Exception {
        // given
        given(postService.getReposts(10L)).willReturn(List.of(
                Post.builder().id(14L).user(authorUser(2L, "user2")).repostId(10L).build()));

        // when & then
        mockMvc.perform(get("/api/v1/posts/10/reposts")
                        .with(user(principal(1L, "user1"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].repostId").value(10));
    }

    @Test
    @DisplayName("비로그인 사용자가 게시글 API에 접근하면 로그인 페이지로 리다이렉트된다")
    void post_api_without_authentication_redirects_to_login() throws Exception {
        // when & then
        mockMvc.perform(get("/api/v1/posts/10"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));

        then(postService).shouldHaveNoInteractions();
    }

    private static User authorUser(Long id, String username) {
        return User.builder().id(id).username(username).password("encoded").build();
    }

    private static UserPrincipal principal(Long id, String username) {
        return new UserPrincipal(authorUser(id, username));
    }
}
