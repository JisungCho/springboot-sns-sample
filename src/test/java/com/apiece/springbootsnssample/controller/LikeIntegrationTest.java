package com.apiece.springbootsnssample.controller;

import com.apiece.springbootsnssample.domain.follow.FollowRepository;
import com.apiece.springbootsnssample.domain.followcount.FollowCountRepository;
import com.apiece.springbootsnssample.domain.like.LikeRepository;
import com.apiece.springbootsnssample.domain.post.PostRepository;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

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
class LikeIntegrationTest {

    private static final String LOGIN_PROCESSING_URL = "/api/v1/login";
    private static final String RAW_PASSWORD = "password123";
    private static final String CONTENT_TYPE = "application/json";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private LikeRepository likeRepository;

    @Autowired
    private FollowRepository followRepository;

    @Autowired
    private FollowCountRepository followCountRepository;

    private Long user1Id;

    @BeforeEach
    void setUp() {
        likeRepository.deleteAll();
        followRepository.deleteAll();
        followCountRepository.deleteAll();
        postRepository.deleteAll();
        userRepository.deleteAll();

        User user1 = userService.signup(new User("user1", RAW_PASSWORD));
        userService.signup(new User("user2", RAW_PASSWORD));
        user1Id = user1.getId();
    }

    private Cookie sessionOf(String username) throws Exception {
        MvcResult loginResult = mockMvc.perform(
                        formLogin().loginProcessingUrl(LOGIN_PROCESSING_URL).user(username).password(RAW_PASSWORD))
                .andExpect(status().isOk())
                .andReturn();
        Cookie sessionCookie = loginResult.getResponse().getCookie("SESSION");
        assertThat(sessionCookie).isNotNull();
        return sessionCookie;
    }

    private long createPost(Cookie cookie, String content) throws Exception {
        String body = mockMvc.perform(post("/api/v1/posts")
                        .cookie(cookie)
                        .with(csrf())
                        .contentType(CONTENT_TYPE)
                        .content("{\"content\":\"" + content + "\"}"))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return extractId(body);
    }

    private static long extractId(String json) {
        int start = json.indexOf("\"id\":") + 5;
        int end = start;
        while (end < json.length() && Character.isDigit(json.charAt(end))) {
            end++;
        }
        return Long.parseLong(json.substring(start, end));
    }

    @Test
    @DisplayName("좋아요하면 likeCount가 증가하고 응답에 현재 카운트가 담긴다")
    void like_increments_post_like_count() throws Exception {
        Cookie user1Session = sessionOf("user1");
        Cookie user2Session = sessionOf("user2");
        long postId = createPost(user1Session, "좋아요 대상 글");

        mockMvc.perform(post("/api/v1/posts/" + postId + "/likes")
                        .cookie(user2Session)
                        .with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.postId").value(postId))
                .andExpect(jsonPath("$.likeCount").value(1));

        mockMvc.perform(get("/api/v1/posts/" + postId).cookie(user1Session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.likeCount").value(1));

        // 다른 사용자가 좋아요하면 2가 된다
        mockMvc.perform(post("/api/v1/posts/" + postId + "/likes")
                        .cookie(user1Session)
                        .with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.likeCount").value(2));
    }

    @Test
    @DisplayName("중복 좋아요는 409이고 카운트는 늘지 않는다")
    void duplicate_like_returns_409_without_increment() throws Exception {
        Cookie user1Session = sessionOf("user1");
        Cookie user2Session = sessionOf("user2");
        long postId = createPost(user1Session, "게시글");

        mockMvc.perform(post("/api/v1/posts/" + postId + "/likes")
                        .cookie(user2Session)
                        .with(csrf()))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/posts/" + postId + "/likes")
                        .cookie(user2Session)
                        .with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("이미 좋아요한 게시글입니다."));

        mockMvc.perform(get("/api/v1/posts/" + postId).cookie(user1Session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.likeCount").value(1));
    }

    @Test
    @DisplayName("좋아요 취소하면 likeCount가 감소하고, 재취소는 404다")
    void unlike_decrements_like_count() throws Exception {
        Cookie user1Session = sessionOf("user1");
        Cookie user2Session = sessionOf("user2");
        long postId = createPost(user1Session, "게시글");

        mockMvc.perform(post("/api/v1/posts/" + postId + "/likes")
                        .cookie(user2Session)
                        .with(csrf()))
                .andExpect(status().isCreated());

        mockMvc.perform(delete("/api/v1/posts/" + postId + "/likes")
                        .cookie(user2Session)
                        .with(csrf()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/posts/" + postId).cookie(user1Session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.likeCount").value(0));

        mockMvc.perform(delete("/api/v1/posts/" + postId + "/likes")
                        .cookie(user2Session)
                        .with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("좋아요한 게시글이 아닙니다."));
    }

    @Test
    @DisplayName("좋아요한 게시글 목록은 최근 좋아요 순으로 반환된다")
    void liked_posts_are_ordered_by_latest_like() throws Exception {
        Cookie user1Session = sessionOf("user1");
        Cookie user2Session = sessionOf("user2");
        long firstPostId = createPost(user1Session, "첫 번째 글");
        long secondPostId = createPost(user1Session, "두 번째 글");

        mockMvc.perform(post("/api/v1/posts/" + firstPostId + "/likes")
                        .cookie(user2Session)
                        .with(csrf()))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/posts/" + secondPostId + "/likes")
                        .cookie(user2Session)
                        .with(csrf()))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/liked-posts").cookie(user2Session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(secondPostId))
                .andExpect(jsonPath("$[1].id").value(firstPostId));

        // 좋아요하지 않은 사용자는 빈 목록을 받는다
        mockMvc.perform(get("/api/v1/liked-posts").cookie(user1Session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("존재하지 않는 게시글에 좋아요하면 404다")
    void like_missing_post_returns_404() throws Exception {
        Cookie user2Session = sessionOf("user2");

        mockMvc.perform(post("/api/v1/posts/999999/likes")
                        .cookie(user2Session)
                        .with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("존재하지 않는 게시글입니다."));
    }

    @Test
    @DisplayName("리포스트를 대상으로 좋아요하면 원본 글의 likeCount가 증가한다")
    void like_on_repost_increments_original_like_count() throws Exception {
        Cookie user1Session = sessionOf("user1");
        Cookie user2Session = sessionOf("user2");
        long postId = createPost(user1Session, "원본 글");

        String repostBody = mockMvc.perform(post("/api/v1/posts/" + postId + "/reposts")
                        .cookie(user2Session)
                        .with(csrf()))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        long repostId = extractId(repostBody);

        mockMvc.perform(post("/api/v1/posts/" + repostId + "/likes")
                        .cookie(user1Session)
                        .with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.postId").value(postId));

        mockMvc.perform(get("/api/v1/posts/" + postId).cookie(user1Session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.likeCount").value(1));

        mockMvc.perform(get("/api/v1/posts/" + repostId).cookie(user1Session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.likeCount").value(0));

        assertThat(user1Id).isNotNull();
    }
}
