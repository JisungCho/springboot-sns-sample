package com.apiece.springbootsnssample.domain.like;

import com.apiece.springbootsnssample.domain.post.Post;
import com.apiece.springbootsnssample.domain.post.PostRepository;
import com.apiece.springbootsnssample.domain.post.PostService;
import com.apiece.springbootsnssample.domain.user.User;import java.util.List;
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
class LikeServiceTest {

    @Mock
    private LikeRepository likeRepository;

    @Mock
    private PostRepository postRepository;

    @Mock
    private PostService postService;

    @InjectMocks
    private LikeService likeService;

    private User author;
    private Post post;

    @BeforeEach
    void setUp() {
        author = User.builder().id(1L).username("user1").password("encoded").build();
        post = Post.builder().id(10L).user(author).content("content").build();
    }

    @Test
    @DisplayName("좋아요하면 좋아요를 저장하고, 같은 트랜잭션에서 읽은 likeCount를 함께 반환한다")
    void like_saves_and_returns_like_count() {
        // given
        Like saved = Like.builder().id(100L).userId(2L).postId(10L).build();
        given(postService.getNormalizedTarget(10L)).willReturn(post);
        given(likeRepository.existsByUserIdAndPostId(2L, 10L)).willReturn(false);
        given(likeRepository.save(any(Like.class))).willReturn(saved);
        given(postRepository.findLikeCountById(10L)).willReturn(Optional.of(1));

        // when
        LikeResult result = likeService.like(2L, 10L);

        // then
        assertThat(result.id()).isEqualTo(100L);
        assertThat(result.userId()).isEqualTo(2L);
        assertThat(result.postId()).isEqualTo(10L);
        assertThat(result.likeCount()).isEqualTo(1);
        then(postRepository).should().incrementLikeCount(10L);
    }

    @Test
    @DisplayName("이미 좋아요한 글이면 LikeException(CONFLICT)이 발생하고 카운트는 증가하지 않는다")
    void like_duplicate_throws_conflict() {
        // given
        given(postService.getNormalizedTarget(10L)).willReturn(post);
        given(likeRepository.existsByUserIdAndPostId(2L, 10L)).willReturn(true);

        // when
        LikeException exception = catchThrowableOfType(
                LikeException.class, () -> likeService.like(2L, 10L));

        // then
        assertThat(exception.getStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(exception.getMessage()).isEqualTo("이미 좋아요한 게시글입니다.");
        then(likeRepository).should(never()).save(any(Like.class));
        then(postRepository).should(never()).incrementLikeCount(anyLong());
    }

    @Test
    @DisplayName("unique 제약 위반이면 LikeException(CONFLICT)이 발생하고 카운트는 증가하지 않는다")
    void like_unique_violation_throws_conflict() {
        // given
        given(postService.getNormalizedTarget(10L)).willReturn(post);
        given(likeRepository.existsByUserIdAndPostId(2L, 10L)).willReturn(false);
        given(likeRepository.save(any(Like.class)))
                .willThrow(new DataIntegrityViolationException("uk_likes_user_id_post_id"));

        // when
        LikeException exception = catchThrowableOfType(
                LikeException.class, () -> likeService.like(2L, 10L));

        // then
        assertThat(exception.getStatus()).isEqualTo(HttpStatus.CONFLICT);
        then(postRepository).should(never()).incrementLikeCount(anyLong());
    }

    @Test
    @DisplayName("좋아요 취소하면 좋아요를 삭제하고 게시글의 likeCount를 감소시킨다")
    void unlike_deletes_and_decrements_like_count() {
        // given
        given(postService.getNormalizedTarget(10L)).willReturn(post);
        given(likeRepository.deleteByUserIdAndPostId(2L, 10L)).willReturn(1);

        // when
        likeService.unlike(2L, 10L);

        // then
        then(postRepository).should().decrementLikeCount(10L);
    }

    @Test
    @DisplayName("좋아요한 적이 없으면 취소 시 LikeException(NOT_FOUND)이 발생한다")
    void unlike_missing_throws_not_found() {
        // given
        given(postService.getNormalizedTarget(10L)).willReturn(post);
        given(likeRepository.deleteByUserIdAndPostId(2L, 10L)).willReturn(0);

        // when
        LikeException exception = catchThrowableOfType(
                LikeException.class, () -> likeService.unlike(2L, 10L));

        // then
        assertThat(exception.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(exception.getMessage()).isEqualTo("좋아요한 게시글이 아닙니다.");
        then(postRepository).should(never()).decrementLikeCount(anyLong());
    }

    @Test
    @DisplayName("리포스트를 대상으로 좋아요하면 원본 글에 반영한다")
    void like_on_repost_targets_original() {
        // given
        Post original = Post.builder().id(10L).user(author).content("original").build();
        Post repost = Post.builder().id(20L).user(author).repostId(10L).build();
        Like saved = Like.builder().id(100L).userId(2L).postId(10L).build();
        given(postService.getNormalizedTarget(20L)).willReturn(original);
        given(likeRepository.existsByUserIdAndPostId(2L, 10L)).willReturn(false);
        given(likeRepository.save(any(Like.class))).willReturn(saved);
        given(postRepository.findLikeCountById(10L)).willReturn(Optional.of(1));

        // when
        LikeResult result = likeService.like(2L, 20L);

        // then
        assertThat(result.postId()).isEqualTo(10L);
        then(postRepository).should().incrementLikeCount(10L);
        then(postRepository).should(never()).incrementLikeCount(20L);
    }

    @Test
    @DisplayName("좋아요한 게시글 목록은 좋아요한 순서를 유지한다")
    void liked_posts_preserve_like_order() {
        // given
        Post first = Post.builder().id(10L).user(author).content("first").build();
        Post second = Post.builder().id(11L).user(author).content("second").build();
        given(likeRepository.findAllByUserIdOrderByCreatedAtDesc(2L)).willReturn(List.of(
                Like.builder().id(100L).userId(2L).postId(11L).build(),
                Like.builder().id(101L).userId(2L).postId(10L).build()));
        // 리포지토리 조회 순서는 보장되지 않으므로 반대 순서로 반환한다.
        given(postRepository.findAllByIdIn(List.of(11L, 10L))).willReturn(List.of(first, second));

        // when
        List<Post> result = likeService.getLikedPosts(2L);

        // then
        assertThat(result).extracting(Post::getId).containsExactly(11L, 10L);
    }

    @Test
    @DisplayName("좋아요한 게시글이 없으면 빈 목록을 반환한다")
    void liked_posts_empty_returns_empty_list() {
        // given
        given(likeRepository.findAllByUserIdOrderByCreatedAtDesc(2L)).willReturn(List.of());

        // when & then
        assertThat(likeService.getLikedPosts(2L)).isEmpty();
        then(postRepository).should(never()).findAllByIdIn(any());
    }

    @Test
    @DisplayName("좋아요 저장 직후 게시글이 사라졌으면 LikeException(NOT_FOUND)이 발생한다")
    void like_missing_post_after_save_throws_not_found() {
        // given
        given(postService.getNormalizedTarget(10L)).willReturn(post);
        given(likeRepository.existsByUserIdAndPostId(2L, 10L)).willReturn(false);
        given(likeRepository.save(any(Like.class)))
                .willReturn(Like.builder().id(100L).userId(2L).postId(10L).build());
        given(postRepository.findLikeCountById(10L)).willReturn(Optional.empty());

        // when
        LikeException exception = catchThrowableOfType(
                LikeException.class, () -> likeService.like(2L, 10L));

        // then
        assertThat(exception.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(exception.getMessage()).isEqualTo("존재하지 않는 게시글입니다.");
    }
}
