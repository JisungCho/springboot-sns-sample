package com.apiece.springbootsnssample.controller;

import com.apiece.springbootsnssample.domain.follow.FollowRepository;
import com.apiece.springbootsnssample.domain.followcount.FollowCountRepository;
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

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class PostIntegrationTest {

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
    private FollowRepository followRepository;

    @Autowired
    private FollowCountRepository followCountRepository;

    private Long user1Id;
    private Long user2Id;

    @BeforeEach
    void setUp() {
        followRepository.deleteAll();
        followCountRepository.deleteAll();
        postRepository.deleteAll();
        userRepository.deleteAll();

        User user1 = userService.signup(new User("user1", RAW_PASSWORD));
        User user2 = userService.signup(new User("user2", RAW_PASSWORD));
        user1Id = user1.getId();
        user2Id = user2.getId();
    }

    private Cookie sessionOf(String username) throws Exception {
        MvcResult loginResult = mockMvc.perform(
                        formLogin().loginProcessingUrl(LOGIN_PROCESSING_URL).user(username).password(RAW_PASSWORD))
                .andExpect(status().isOk())
                .andReturn();
        Cookie sessionCookie = loginResult.getResponse().getCookie("SESSION");
        org.assertj.core.api.Assertions.assertThat(sessionCookie).isNotNull();
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

    private long createChild(Cookie cookie, long parentId, String path, String content) throws Exception {
        String body = mockMvc.perform(post("/api/v1/posts/" + parentId + "/" + path)
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
    @DisplayName("게시글 작성 → 조회 → 답글 → 인용 → 리포스트 → 취소 → 삭제 흐름")
    void post_lifecycle() throws Exception {
        Cookie user1Session = sessionOf("user1");
        Cookie user2Session = sessionOf("user2");

        // 게시글 작성
        long postId = createPost(user1Session, "첫 게시글");

        // 단건 조회 시 작성자 정보와 함께 반환된다 (조회수는 POST /views 로 집계한다)
        mockMvc.perform(get("/api/v1/posts/" + postId).cookie(user2Session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(postId))
                .andExpect(jsonPath("$.content").value("첫 게시글"))
                .andExpect(jsonPath("$.authorId").value(user1Id))
                .andExpect(jsonPath("$.authorUsername").value("user1"))
                .andExpect(jsonPath("$.viewCount").value(0));

        // 답글 작성 후 부모의 replyCount가 증가한다
        long replyId = createChild(user2Session, postId, "replies", "답글");

        mockMvc.perform(get("/api/v1/posts/" + postId).cookie(user1Session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.replyCount").value(1));

        mockMvc.perform(get("/api/v1/posts/" + postId + "/replies").cookie(user1Session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(replyId))
                .andExpect(jsonPath("$[0].parentId").value(postId))
                .andExpect(jsonPath("$[0].authorUsername").value("user2"));

        // 인용글 작성 후 원본의 replyCount가 증가한다
        long quoteId = createChild(user2Session, postId, "quotes", "인용글");

        mockMvc.perform(get("/api/v1/posts/" + postId).cookie(user1Session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.replyCount").value(2));

        mockMvc.perform(get("/api/v1/posts/" + postId + "/quotes").cookie(user1Session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(quoteId))
                .andExpect(jsonPath("$[0].quoteId").value(postId));

        // 리포스트는 본문 없이 만들어지고 원본의 repostCount가 증가한다
        String repostBody = mockMvc.perform(post("/api/v1/posts/" + postId + "/reposts")
                        .cookie(user2Session)
                        .with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.repostId").value(postId))
                .andExpect(jsonPath("$.content").doesNotExist())
                .andReturn()
                .getResponse()
                .getContentAsString();
        long repostId = extractId(repostBody);

        mockMvc.perform(get("/api/v1/posts/" + postId).cookie(user1Session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.repostCount").value(1));

        mockMvc.perform(get("/api/v1/posts/" + postId + "/reposts").cookie(user1Session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(repostId));

        // 리포스트를 대상으로 한 답글은 원본에 달린다
        mockMvc.perform(post("/api/v1/posts/" + repostId + "/replies")
                        .cookie(user1Session)
                        .with(csrf())
                        .contentType(CONTENT_TYPE)
                        .content("{\"content\":\"리포스트에 단 답글\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.parentId").value(postId));

        mockMvc.perform(get("/api/v1/posts/" + postId + "/replies").cookie(user1Session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));

        // 중복 리포스트는 409
        mockMvc.perform(post("/api/v1/posts/" + postId + "/reposts")
                        .cookie(user2Session)
                        .with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("이미 리포스트한 게시글입니다."));

        // 리포스트 취소 후 repostCount가 감소하고 재리포스트가 가능하다
        mockMvc.perform(delete("/api/v1/posts/" + postId + "/reposts")
                        .cookie(user2Session)
                        .with(csrf()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/posts/" + postId).cookie(user1Session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.repostCount").value(0));

        mockMvc.perform(delete("/api/v1/posts/" + postId + "/reposts")
                        .cookie(user2Session)
                        .with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("리포스트한 게시글이 아닙니다."));

        mockMvc.perform(post("/api/v1/posts/" + postId + "/reposts")
                        .cookie(user2Session)
                        .with(csrf()))
                .andExpect(status().isCreated());

        // 본인 글만 삭제할 수 있다
        mockMvc.perform(delete("/api/v1/posts/" + postId)
                        .cookie(user2Session)
                        .with(csrf()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("본인이 작성한 게시글만 삭제할 수 있습니다."));

        // 삭제 후에는 조회가 404
        mockMvc.perform(delete("/api/v1/posts/" + postId)
                        .cookie(user1Session)
                        .with(csrf()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/posts/" + postId).cookie(user1Session))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("존재하지 않는 게시글입니다."));
    }

    @Test
    @DisplayName("답글 삭제 시 부모의 replyCount가 감소한다")
    void delete_reply_decrements_parent_reply_count() throws Exception {
        Cookie user1Session = sessionOf("user1");
        Cookie user2Session = sessionOf("user2");

        long postId = createPost(user1Session, "게시글");
        long replyId = createChild(user2Session, postId, "replies", "답글");

        mockMvc.perform(get("/api/v1/posts/" + postId).cookie(user1Session))
                .andExpect(jsonPath("$.replyCount").value(1));

        mockMvc.perform(delete("/api/v1/posts/" + replyId)
                        .cookie(user2Session)
                        .with(csrf()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/posts/" + postId).cookie(user1Session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.replyCount").value(0));

        mockMvc.perform(get("/api/v1/posts/" + postId + "/replies").cookie(user1Session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("인용글 삭제 시 원본의 replyCount가 감소한다")
    void delete_quote_decrements_original_reply_count() throws Exception {
        Cookie user1Session = sessionOf("user1");
        Cookie user2Session = sessionOf("user2");

        long postId = createPost(user1Session, "게시글");
        long quoteId = createChild(user2Session, postId, "quotes", "인용글");

        mockMvc.perform(get("/api/v1/posts/" + postId).cookie(user1Session))
                .andExpect(jsonPath("$.replyCount").value(1));

        mockMvc.perform(delete("/api/v1/posts/" + quoteId)
                        .cookie(user2Session)
                        .with(csrf()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/posts/" + postId).cookie(user1Session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.replyCount").value(0));

        mockMvc.perform(get("/api/v1/posts/" + postId + "/quotes").cookie(user1Session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("본문이 공백이거나 500자를 넘으면 400을 반환한다")
    void create_post_validates_content() throws Exception {
        Cookie user1Session = sessionOf("user1");

        mockMvc.perform(post("/api/v1/posts")
                        .cookie(user1Session)
                        .with(csrf())
                        .contentType(CONTENT_TYPE)
                        .content("{\"content\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("본문은 필수입니다."));

        String tooLong = "a".repeat(501);
        mockMvc.perform(post("/api/v1/posts")
                        .cookie(user1Session)
                        .with(csrf())
                        .contentType(CONTENT_TYPE)
                        .content("{\"content\":\"" + tooLong + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("본문은 500자 이하로 입력해주세요."));
    }

    @Test
    @DisplayName("비로그인 사용자가 게시글 작성 API에 접근하면 리다이렉트된다")
    void create_post_without_authentication_redirects() throws Exception {
        mockMvc.perform(post("/api/v1/posts")
                        .with(csrf())
                        .contentType(CONTENT_TYPE)
                        .content("{\"content\":\"content\"}"))
                .andExpect(status().is3xxRedirection());
    }
}
