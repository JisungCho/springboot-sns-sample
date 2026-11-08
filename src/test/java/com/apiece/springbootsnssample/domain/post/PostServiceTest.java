package com.apiece.springbootsnssample.domain.post;

import com.apiece.springbootsnssample.domain.user.User;
import com.apiece.springbootsnssample.domain.user.UserRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class PostServiceTest {

    @Mock
    private PostRepository postRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private PostService postService;

    private User author;

    @BeforeEach
    void setUp() {
        author = User.builder().id(1L).username("user1").password("encoded").build();
    }

    @Test
    @DisplayName("게시글을 작성하면 저장된 Post를 반환한다")
    void create_post_saves() {
        // given
        Post saved = Post.builder().id(10L).user(author).content("content").build();
        given(userRepository.findById(1L)).willReturn(Optional.of(author));
        given(postRepository.save(any(Post.class))).willReturn(saved);

        // when
        Post result = postService.createPost(1L, "content");

        // then
        assertThat(result.getId()).isEqualTo(10L);
        assertThat(result.getContent()).isEqualTo("content");
        then(postRepository).should().save(any(Post.class));
    }

    @Test
    @DisplayName("본문이 공백이면 PostException(BAD_REQUEST)이 발생한다")
    void create_post_blank_content_throws_bad_request() {
        // when
        PostException exception = catchThrowableOfType(
                PostException.class, () -> postService.createPost(1L, "   "));

        // then
        assertThat(exception.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(exception.getMessage()).isEqualTo("본문은 필수입니다.");
        then(postRepository).should(never()).save(any(Post.class));
    }

    @Test
    @DisplayName("본문이 500자를 넘으면 PostException(BAD_REQUEST)이 발생한다")
    void create_post_too_long_content_throws_bad_request() {
        // given
        String content = "a".repeat(Post.MAX_CONTENT_LENGTH + 1);

        // when
        PostException exception = catchThrowableOfType(
                PostException.class, () -> postService.createPost(1L, content));

        // then
        assertThat(exception.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        then(postRepository).should(never()).save(any(Post.class));
    }

    @Test
    @DisplayName("존재하지 않는 사용자면 PostException(NOT_FOUND)이 발생한다")
    void create_post_missing_user_throws_not_found() {
        // given
        given(userRepository.findById(1L)).willReturn(Optional.empty());

        // when
        PostException exception = catchThrowableOfType(
                PostException.class, () -> postService.createPost(1L, "content"));

        // then
        assertThat(exception.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(exception.getMessage()).isEqualTo("존재하지 않는 사용자입니다.");
    }

    @Test
    @DisplayName("답글을 작성하면 부모의 replyCount를 증가시킨다")
    void create_reply_increments_parent_reply_count() {
        // given
        Post parent = Post.builder().id(10L).user(author).content("parent").build();
        Post saved = Post.builder().id(11L).user(author).content("reply").parentId(10L).build();
        given(userRepository.findById(1L)).willReturn(Optional.of(author));
        given(postRepository.findById(10L)).willReturn(Optional.of(parent));
        given(postRepository.save(any(Post.class))).willReturn(saved);

        // when
        Post result = postService.createReply(1L, 10L, "reply");

        // then
        assertThat(result.getParentId()).isEqualTo(10L);
        then(postRepository).should().incrementReplyCount(10L);
    }

    @Test
    @DisplayName("존재하지 않는 게시글에 답글을 달면 PostException(NOT_FOUND)이 발생한다")
    void create_reply_missing_parent_throws_not_found() {
        // given
        given(userRepository.findById(1L)).willReturn(Optional.of(author));
        given(postRepository.findById(10L)).willReturn(Optional.empty());

        // when
        PostException exception = catchThrowableOfType(
                PostException.class, () -> postService.createReply(1L, 10L, "reply"));

        // then
        assertThat(exception.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(exception.getMessage()).isEqualTo("존재하지 않는 게시글입니다.");
        then(postRepository).should(never()).incrementReplyCount(anyLong());
    }

    @Test
    @DisplayName("리포스트를 대상으로 답글을 달면 원본 글에 답글이 달린다")
    void create_reply_on_repost_targets_original() {
        // given
        Post original = Post.builder().id(10L).user(author).content("original").build();
        Post repost = Post.builder().id(20L).user(author).repostId(10L).build();
        Post saved = Post.builder().id(21L).user(author).content("reply").parentId(10L).build();
        given(userRepository.findById(1L)).willReturn(Optional.of(author));
        given(postRepository.findById(20L)).willReturn(Optional.of(repost));
        given(postRepository.findById(10L)).willReturn(Optional.of(original));
        given(postRepository.save(any(Post.class))).willReturn(saved);

        // when
        Post result = postService.createReply(1L, 20L, "reply");

        // then
        assertThat(result.getParentId()).isEqualTo(10L);
        then(postRepository).should().incrementReplyCount(10L);
    }

    @Test
    @DisplayName("인용글을 작성하면 원본의 replyCount를 증가시킨다")
    void create_quote_increments_reply_count() {
        // given
        Post original = Post.builder().id(10L).user(author).content("original").build();
        Post saved = Post.builder().id(12L).user(author).content("quote").quoteId(10L).build();
        given(userRepository.findById(1L)).willReturn(Optional.of(author));
        given(postRepository.findById(10L)).willReturn(Optional.of(original));
        given(postRepository.save(any(Post.class))).willReturn(saved);

        // when
        Post result = postService.createQuote(1L, 10L, "quote");

        // then
        assertThat(result.getQuoteId()).isEqualTo(10L);
        then(postRepository).should().incrementReplyCount(10L);
    }

    @Test
    @DisplayName("리포스트하면 원본의 repostCount를 증가시킨다")
    void create_repost_increments_repost_count() {
        // given
        Post original = Post.builder().id(10L).user(author).content("original").build();
        Post saved = Post.builder().id(13L).user(author).repostId(10L).build();
        given(userRepository.findById(1L)).willReturn(Optional.of(author));
        given(postRepository.findById(10L)).willReturn(Optional.of(original));
        given(postRepository.existsByUserIdAndRepostId(1L, 10L)).willReturn(false);
        given(postRepository.save(any(Post.class))).willReturn(saved);

        // when
        Post result = postService.createRepost(1L, 10L);

        // then
        assertThat(result.getRepostId()).isEqualTo(10L);
        assertThat(result.getContent()).isNull();
        then(postRepository).should().incrementRepostCount(10L);
    }

    @Test
    @DisplayName("이미 리포스트한 글이면 PostException(CONFLICT)이 발생한다")
    void create_repost_duplicate_throws_conflict() {
        // given
        Post original = Post.builder().id(10L).user(author).content("original").build();
        given(userRepository.findById(1L)).willReturn(Optional.of(author));
        given(postRepository.findById(10L)).willReturn(Optional.of(original));
        given(postRepository.existsByUserIdAndRepostId(1L, 10L)).willReturn(true);

        // when
        PostException exception = catchThrowableOfType(
                PostException.class, () -> postService.createRepost(1L, 10L));

        // then
        assertThat(exception.getStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(exception.getMessage()).isEqualTo("이미 리포스트한 게시글입니다.");
        then(postRepository).should(never()).incrementRepostCount(anyLong());
    }

    @Test
    @DisplayName("unique 제약 위반이면 PostException(CONFLICT)이 발생하고 카운트는 증가하지 않는다")
    void create_repost_unique_violation_throws_conflict() {
        // given
        Post original = Post.builder().id(10L).user(author).content("original").build();
        given(userRepository.findById(1L)).willReturn(Optional.of(author));
        given(postRepository.findById(10L)).willReturn(Optional.of(original));
        given(postRepository.existsByUserIdAndRepostId(1L, 10L)).willReturn(false);
        given(postRepository.save(any(Post.class)))
                .willThrow(new DataIntegrityViolationException("uk_posts_user_id_repost_id"));

        // when
        PostException exception = catchThrowableOfType(
                PostException.class, () -> postService.createRepost(1L, 10L));

        // then
        assertThat(exception.getStatus()).isEqualTo(HttpStatus.CONFLICT);
        then(postRepository).should(never()).incrementRepostCount(anyLong());
    }

    @Test
    @DisplayName("리포스트를 취소하면 리포스트를 삭제하고 원본의 repostCount를 감소시킨다")
    void cancel_repost_deletes_and_decrements() {
        // given
        Post original = Post.builder().id(10L).user(author).content("original").build();
        Post repost = Post.builder().id(20L).user(author).repostId(10L).build();
        given(postRepository.findById(10L)).willReturn(Optional.of(original));
        given(postRepository.findByUserIdAndRepostId(1L, 10L)).willReturn(Optional.of(repost));

        // when
        postService.cancelRepost(1L, 10L);

        // then
        then(postRepository).should().delete(repost);
        then(postRepository).should().decrementRepostCount(10L);
    }

    @Test
    @DisplayName("리포스트한 적이 없으면 취소 시 PostException(NOT_FOUND)이 발생한다")
    void cancel_repost_missing_throws_not_found() {
        // given
        Post original = Post.builder().id(10L).user(author).content("original").build();
        given(postRepository.findById(10L)).willReturn(Optional.of(original));
        given(postRepository.findByUserIdAndRepostId(1L, 10L)).willReturn(Optional.empty());

        // when
        PostException exception = catchThrowableOfType(
                PostException.class, () -> postService.cancelRepost(1L, 10L));

        // then
        assertThat(exception.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(exception.getMessage()).isEqualTo("리포스트한 게시글이 아닙니다.");
        then(postRepository).should(never()).decrementRepostCount(anyLong());
    }

    @Test
    @DisplayName("게시글을 조회하면 viewCount를 증가시키고 작성자와 함께 반환한다")
    void get_post_increments_view_count() {
        // given
        Post post = Post.builder().id(10L).user(author).content("content").build();
        given(postRepository.incrementViewCount(10L)).willReturn(1);
        given(postRepository.findWithUserById(10L)).willReturn(Optional.of(post));

        // when
        Post result = postService.getPost(10L);

        // then
        assertThat(result.getId()).isEqualTo(10L);
        then(postRepository).should().incrementViewCount(10L);
    }

    @Test
    @DisplayName("존재하지 않는 게시글을 조회하면 PostException(NOT_FOUND)이 발생한다")
    void get_missing_post_throws_not_found() {
        // given
        given(postRepository.incrementViewCount(10L)).willReturn(0);

        // when
        PostException exception = catchThrowableOfType(
                PostException.class, () -> postService.getPost(10L));

        // then
        assertThat(exception.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(exception.getMessage()).isEqualTo("존재하지 않는 게시글입니다.");
    }

    @Test
    @DisplayName("본인 글이 아니면 삭제 시 PostException(FORBIDDEN)이 발생한다")
    void delete_post_not_owner_throws_forbidden() {
        // given
        Post post = Post.builder().id(10L).user(author).content("content").build();
        given(postRepository.findById(10L)).willReturn(Optional.of(post));

        // when
        PostException exception = catchThrowableOfType(
                PostException.class, () -> postService.deletePost(2L, 10L));

        // then
        assertThat(exception.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(exception.getMessage()).isEqualTo("본인이 작성한 게시글만 삭제할 수 있습니다.");
        then(postRepository).should(never()).delete(any(Post.class));
    }

    @Test
    @DisplayName("답글을 삭제하면 부모의 replyCount를 감소시킨다")
    void delete_reply_decrements_parent_reply_count() {
        // given
        Post reply = Post.builder().id(11L).user(author).content("reply").parentId(10L).build();
        given(postRepository.findById(11L)).willReturn(Optional.of(reply));

        // when
        postService.deletePost(1L, 11L);

        // then
        then(postRepository).should().delete(reply);
        then(postRepository).should().decrementReplyCount(10L);
        then(postRepository).should(never()).decrementRepostCount(anyLong());
    }

    @Test
    @DisplayName("인용글을 삭제하면 원본의 replyCount를 감소시킨다")
    void delete_quote_decrements_original_reply_count() {
        // given
        Post quote = Post.builder().id(12L).user(author).content("quote").quoteId(10L).build();
        given(postRepository.findById(12L)).willReturn(Optional.of(quote));

        // when
        postService.deletePost(1L, 12L);

        // then
        then(postRepository).should().delete(quote);
        then(postRepository).should().decrementReplyCount(10L);
        then(postRepository).should(never()).decrementRepostCount(anyLong());
    }

    @Test
    @DisplayName("리포스트를 삭제하면 원본의 repostCount를 감소시킨다")
    void delete_repost_decrements_repost_count() {
        // given
        Post repost = Post.builder().id(20L).user(author).repostId(10L).build();
        given(postRepository.findById(20L)).willReturn(Optional.of(repost));

        // when
        postService.deletePost(1L, 20L);

        // then
        then(postRepository).should().delete(repost);
        then(postRepository).should().decrementRepostCount(10L);
        then(postRepository).should(never()).decrementReplyCount(anyLong());
    }

    @Test
    @DisplayName("답글 목록/인용 목록/리포스트 목록은 리포지토리 결과를 그대로 반환한다")
    void lists_delegate_to_repository() {
        // given
        Post post = Post.builder().id(11L).user(author).content("reply").parentId(10L).build();
        given(postRepository.findAllByParentIdOrderByCreatedAtAsc(10L)).willReturn(List.of(post));
        given(postRepository.findAllByQuoteIdOrderByCreatedAtDesc(10L)).willReturn(List.of(post));
        given(postRepository.findAllByRepostIdOrderByCreatedAtDesc(10L)).willReturn(List.of(post));

        // when & then
        assertThat(postService.getReplies(10L)).containsExactly(post);
        assertThat(postService.getQuotes(10L)).containsExactly(post);
        assertThat(postService.getReposts(10L)).containsExactly(post);
    }
}
