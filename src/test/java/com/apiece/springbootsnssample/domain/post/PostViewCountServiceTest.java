package com.apiece.springbootsnssample.domain.post;

import java.util.LinkedHashSet;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.lenient;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class PostViewCountServiceTest {

    private static final String DIRTY_SET_KEY = "post:view:dirty_set";

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private SetOperations<String, String> setOperations;

    @Mock
    private PostRepository postRepository;

    @InjectMocks
    private PostViewCountService postViewCountService;

    @BeforeEach
    void setUp() {
        // 테스트마다 사용 여부가 다르므로 lenient로 스텁한다.
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        lenient().when(redisTemplate.opsForSet()).thenReturn(setOperations);
    }

    @Test
    @DisplayName("조회수를 올리면 Redis 카운터를 증가시키고 dirty_set에 postId를 넣는다")
    void increase_view_count_writes_to_redis() {
        // given
        given(postRepository.existsById(10L)).willReturn(true);

        // when
        postViewCountService.increaseViewCount(10L);

        // then
        then(valueOperations).should().increment("post:view:10");
        then(setOperations).should().add(DIRTY_SET_KEY, "10");
    }

    @Test
    @DisplayName("존재하지 않는 게시글이면 PostException(NOT_FOUND)이 발생하고 Redis는 건드리지 않는다")
    void increase_view_count_missing_post_throws_not_found() {
        // given
        given(postRepository.existsById(10L)).willReturn(false);

        // when
        PostException exception = catchThrowableOfType(
                PostException.class, () -> postViewCountService.increaseViewCount(10L));

        // then
        assertThat(exception.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(exception.getMessage()).isEqualTo("존재하지 않는 게시글입니다.");
        then(valueOperations).should(never()).increment(anyString());
        then(setOperations).should(never()).add(anyString(), anyString());
    }

    @Test
    @DisplayName("dirty_set이 비어 있으면 반영하지 않고 0을 반환한다")
    void flush_with_empty_dirty_set_does_nothing() {
        // given
        given(setOperations.members(DIRTY_SET_KEY)).willReturn(Set.of());

        // when
        int flushed = postViewCountService.flush();

        // then
        assertThat(flushed).isZero();
        then(valueOperations).should(never()).getAndDelete(anyString());
        then(postRepository).should(never()).incrementViewCountBy(anyLong(), anyLong());
    }

    @Test
    @DisplayName("dirty_set에 있는 게시글의 조회수를 DB에 반영한다")
    void flush_updates_view_count_of_dirty_posts() {
        // given
        given(setOperations.members(DIRTY_SET_KEY)).willReturn(dirtySet("10", "11"));
        given(setOperations.remove(DIRTY_SET_KEY, "10")).willReturn(1L);
        given(setOperations.remove(DIRTY_SET_KEY, "11")).willReturn(1L);
        given(valueOperations.getAndDelete("post:view:10")).willReturn("3");
        given(valueOperations.getAndDelete("post:view:11")).willReturn("5");

        // when
        int flushed = postViewCountService.flush();

        // then
        assertThat(flushed).isEqualTo(2);
        then(postRepository).should().incrementViewCountBy(10L, 3L);
        then(postRepository).should().incrementViewCountBy(11L, 5L);
    }

    @Test
    @DisplayName("다른 인스턴스가 먼저 가져간 postId는 SREM 결과가 0이므로 건너뛴다")
    void flush_skips_post_id_claimed_by_another_instance() {
        // given
        given(setOperations.members(DIRTY_SET_KEY)).willReturn(dirtySet("10", "11"));
        given(setOperations.remove(DIRTY_SET_KEY, "10")).willReturn(0L);
        given(setOperations.remove(DIRTY_SET_KEY, "11")).willReturn(1L);
        given(valueOperations.getAndDelete("post:view:11")).willReturn("2");

        // when
        int flushed = postViewCountService.flush();

        // then
        assertThat(flushed).isEqualTo(1);
        then(valueOperations).should(never()).getAndDelete("post:view:10");
        then(postRepository).should(never()).incrementViewCountBy(10L, 1L);
        then(postRepository).should().incrementViewCountBy(11L, 2L);
    }

    @Test
    @DisplayName("카운터가 이미 비워졌으면 DB를 갱신하지 않는다")
    void flush_skips_post_with_empty_counter() {
        // given
        given(setOperations.members(DIRTY_SET_KEY)).willReturn(dirtySet("10"));
        given(setOperations.remove(DIRTY_SET_KEY, "10")).willReturn(1L);
        given(valueOperations.getAndDelete("post:view:10")).willReturn(null);

        // when
        int flushed = postViewCountService.flush();

        // then
        assertThat(flushed).isZero();
        then(postRepository).should(never()).incrementViewCountBy(anyLong(), anyLong());
    }

    @Test
    @DisplayName("숫자가 아닌 값은 dirty_set에서 제거하고 처리하지 않는다")
    void flush_removes_malformed_post_id() {
        // given
        given(setOperations.members(DIRTY_SET_KEY)).willReturn(dirtySet("not-a-number", "10"));
        given(setOperations.remove(DIRTY_SET_KEY, "not-a-number")).willReturn(1L);
        given(setOperations.remove(DIRTY_SET_KEY, "10")).willReturn(1L);
        given(valueOperations.getAndDelete("post:view:10")).willReturn("1");

        // when
        int flushed = postViewCountService.flush();

        // then
        assertThat(flushed).isEqualTo(1);
        then(setOperations).should().remove(DIRTY_SET_KEY, "not-a-number");
        then(postRepository).should().incrementViewCountBy(10L, 1L);
    }

    @Test
    @DisplayName("DB 반영에 실패하면 키운 카운터를 되돌리고 dirty_set에 다시 올린다")
    void flush_restores_redis_when_db_update_fails() {
        // given
        given(setOperations.members(DIRTY_SET_KEY)).willReturn(dirtySet("10"));
        given(setOperations.remove(DIRTY_SET_KEY, "10")).willReturn(1L);
        given(valueOperations.getAndDelete("post:view:10")).willReturn("4");
        willThrow(new IllegalStateException("db down"))
                .given(postRepository).incrementViewCountBy(10L, 4L);

        // when
        IllegalStateException exception = catchThrowableOfType(
                IllegalStateException.class, () -> postViewCountService.flush());

        // then
        assertThat(exception).hasMessage("db down");
        then(valueOperations).should().increment("post:view:10", 4L);
        then(setOperations).should().add(DIRTY_SET_KEY, "10");
    }

    @Test
    @DisplayName("Redis에서 값을 읽는 도중 실패하면 이미 선점한 postId를 dirty_set에 되돌린다")
    void flush_restores_claimed_post_ids_when_redis_read_fails() {
        // given
        given(setOperations.members(DIRTY_SET_KEY)).willReturn(dirtySet("10"));
        given(setOperations.remove(DIRTY_SET_KEY, "10")).willReturn(1L);
        given(valueOperations.getAndDelete("post:view:10"))
                .willThrow(new IllegalStateException("redis down"));

        // when
        IllegalStateException exception = catchThrowableOfType(
                IllegalStateException.class, () -> postViewCountService.flush());

        // then
        assertThat(exception).hasMessage("redis down");
        then(setOperations).should().add(DIRTY_SET_KEY, "10");
        then(postRepository).should(never()).incrementViewCountBy(anyLong(), anyLong());
    }

    private static Set<String> dirtySet(String... postIds) {
        return new LinkedHashSet<>(Set.of(postIds));
    }
}
