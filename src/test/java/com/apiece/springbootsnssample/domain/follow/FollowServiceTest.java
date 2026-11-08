package com.apiece.springbootsnssample.domain.follow;

import com.apiece.springbootsnssample.domain.followcount.FollowCountService;
import com.apiece.springbootsnssample.domain.user.User;
import com.apiece.springbootsnssample.domain.user.UserRepository;
import java.util.List;
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
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class FollowServiceTest {

    @Mock
    private FollowRepository followRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private FollowCountService followCountService;

    @InjectMocks
    private FollowService followService;

    @Test
    @DisplayName("팔로우에 성공하면 Follow를 저장하고 카운트를 증가시킨다")
    void follow_saves_and_increments_count() {
        // given
        Follow saved = Follow.builder().id(10L).followerId(1L).followeeId(2L).build();
        given(userRepository.existsById(2L)).willReturn(true);
        given(followRepository.existsByFollowerIdAndFolloweeId(1L, 2L)).willReturn(false);
        given(followRepository.save(any(Follow.class))).willReturn(saved);

        // when
        Follow result = followService.follow(1L, 2L);

        // then
        assertThat(result.getId()).isEqualTo(10L);
        assertThat(result.getFollowerId()).isEqualTo(1L);
        assertThat(result.getFolloweeId()).isEqualTo(2L);
        then(followRepository).should().save(any(Follow.class));
        then(followCountService).should().incrementForFollow(1L, 2L);
    }

    @Test
    @DisplayName("자기 자신을 팔로우하면 FollowException(BAD_REQUEST)이 발생한다")
    void follow_self_throws_bad_request() {
        // when
        FollowException exception = catchThrowableOfType(
                FollowException.class, () -> followService.follow(1L, 1L));

        // then
        assertThat(exception.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(exception.getMessage()).isEqualTo("자기 자신을 팔로우할 수 없습니다.");
        then(followRepository).should(never()).save(any(Follow.class));
        then(followCountService).should(never()).incrementForFollow(any(), any());
    }

    @Test
    @DisplayName("존재하지 않는 사용자를 팔로우하면 FollowException(NOT_FOUND)이 발생한다")
    void follow_missing_user_throws_not_found() {
        // given
        given(userRepository.existsById(2L)).willReturn(false);

        // when
        FollowException exception = catchThrowableOfType(
                FollowException.class, () -> followService.follow(1L, 2L));

        // then
        assertThat(exception.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(exception.getMessage()).isEqualTo("존재하지 않는 사용자입니다.");
        then(followRepository).should(never()).save(any(Follow.class));
        then(followCountService).should(never()).incrementForFollow(any(), any());
    }

    @Test
    @DisplayName("이미 팔로우한 사용자면 FollowException(CONFLICT)이 발생한다")
    void follow_duplicate_throws_conflict() {
        // given
        given(userRepository.existsById(2L)).willReturn(true);
        given(followRepository.existsByFollowerIdAndFolloweeId(1L, 2L)).willReturn(true);

        // when
        FollowException exception = catchThrowableOfType(
                FollowException.class, () -> followService.follow(1L, 2L));

        // then
        assertThat(exception.getStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(exception.getMessage()).isEqualTo("이미 팔로우한 사용자입니다.");
        then(followRepository).should(never()).save(any(Follow.class));
        then(followCountService).should(never()).incrementForFollow(any(), any());
    }

    @Test
    @DisplayName("unique 제약 위반이면 FollowException(CONFLICT)이 발생하고 카운트는 증가하지 않는다")
    void follow_unique_violation_throws_conflict() {
        // given
        given(userRepository.existsById(2L)).willReturn(true);
        given(followRepository.existsByFollowerIdAndFolloweeId(1L, 2L)).willReturn(false);
        given(followRepository.save(any(Follow.class)))
                .willThrow(new DataIntegrityViolationException("uk_follows_follower_followee"));

        // when
        FollowException exception = catchThrowableOfType(
                FollowException.class, () -> followService.follow(1L, 2L));

        // then
        assertThat(exception.getStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(exception.getMessage()).isEqualTo("이미 팔로우한 사용자입니다.");
        then(followCountService).should(never()).incrementForFollow(any(), any());
    }

    @Test
    @DisplayName("언팔로우에 성공하면 Follow를 삭제하고 카운트를 감소시킨다")
    void unfollow_deletes_and_decrements_count() {
        // given
        given(followRepository.deleteByFollowerIdAndFolloweeId(1L, 2L)).willReturn(1);

        // when
        followService.unfollow(1L, 2L);

        // then
        then(followRepository).should().deleteByFollowerIdAndFolloweeId(1L, 2L);
        then(followCountService).should().decrementForUnfollow(1L, 2L);
    }

    @Test
    @DisplayName("팔로우 관계가 없으면 언팔로우 시 FollowException(NOT_FOUND)이 발생한다")
    void unfollow_missing_relation_throws_not_found() {
        // given
        given(followRepository.deleteByFollowerIdAndFolloweeId(1L, 2L)).willReturn(0);

        // when
        FollowException exception = catchThrowableOfType(
                FollowException.class, () -> followService.unfollow(1L, 2L));

        // then
        assertThat(exception.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(exception.getMessage()).isEqualTo("팔로우 관계가 존재하지 않습니다.");
        then(followCountService).should(never()).decrementForUnfollow(any(), any());
    }

    @Test
    @DisplayName("getFollowees는 팔로우 목록 순서를 유지한다")
    void get_followees_preserves_order() {
        // given
        Follow newer = Follow.builder().id(2L).followerId(1L).followeeId(3L).build();
        Follow older = Follow.builder().id(1L).followerId(1L).followeeId(2L).build();
        given(followRepository.findAllByFollowerIdOrderByCreatedAtDesc(1L))
                .willReturn(List.of(newer, older));

        User user2 = User.builder().id(2L).username("user2").password("encoded").build();
        User user3 = User.builder().id(3L).username("user3").password("encoded").build();
        given(userRepository.findAllById(any())).willReturn(List.of(user2, user3));

        // when
        List<User> followees = followService.getFollowees(1L);

        // then
        assertThat(followees).extracting(User::getId).containsExactly(3L, 2L);
    }

    @Test
    @DisplayName("getFollowers는 팔로우 목록 순서를 유지한다")
    void get_followers_preserves_order() {
        // given
        Follow newer = Follow.builder().id(2L).followerId(3L).followeeId(1L).build();
        Follow older = Follow.builder().id(1L).followerId(2L).followeeId(1L).build();
        given(followRepository.findAllByFolloweeIdOrderByCreatedAtDesc(1L))
                .willReturn(List.of(newer, older));

        User user2 = User.builder().id(2L).username("user2").password("encoded").build();
        User user3 = User.builder().id(3L).username("user3").password("encoded").build();
        given(userRepository.findAllById(any())).willReturn(List.of(user2, user3));

        // when
        List<User> followers = followService.getFollowers(1L);

        // then
        assertThat(followers).extracting(User::getId).containsExactly(3L, 2L);
    }
}
