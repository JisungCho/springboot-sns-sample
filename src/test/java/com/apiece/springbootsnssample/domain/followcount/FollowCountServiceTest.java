package com.apiece.springbootsnssample.domain.followcount;

import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.inOrder;

@ExtendWith(MockitoExtension.class)
class FollowCountServiceTest {

    @Mock
    private FollowCountRepository followCountRepository;

    @InjectMocks
    private FollowCountService followCountService;

    @Test
    @DisplayName("create는 FollowCount를 0,0으로 저장한다")
    void create_saves_follow_count() {
        // given
        given(followCountRepository.save(any(FollowCount.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        // when
        FollowCount created = followCountService.create(1L);

        // then
        assertThat(created.getUserId()).isEqualTo(1L);
        assertThat(created.getFollowersCount()).isZero();
        assertThat(created.getFolloweesCount()).isZero();
        then(followCountRepository).should().save(any(FollowCount.class));
    }

    @Test
    @DisplayName("incrementForFollow는 팔로워의 followeesCount, 대상의 followersCount 순으로 증가시킨다")
    void increment_for_follow_increments_in_order() {
        // given
        given(followCountRepository.incrementFolloweesCount(1L)).willReturn(1);
        given(followCountRepository.incrementFollowersCount(2L)).willReturn(1);

        // when
        followCountService.incrementForFollow(1L, 2L);

        // then
        InOrder inOrder = inOrder(followCountRepository);
        then(followCountRepository).should(inOrder).incrementFolloweesCount(1L);
        then(followCountRepository).should(inOrder).incrementFollowersCount(2L);
    }

    @Test
    @DisplayName("incrementForFollow는 followeesCount 갱신이 0건이면 IllegalStateException을 던진다")
    void increment_for_follow_throws_when_followees_update_returns_zero() {
        // given
        given(followCountRepository.incrementFolloweesCount(1L)).willReturn(0);
        given(followCountRepository.incrementFollowersCount(2L)).willReturn(1);

        // when & then
        assertThatThrownBy(() -> followCountService.incrementForFollow(1L, 2L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("FollowCount");
    }

    @Test
    @DisplayName("incrementForFollow는 followersCount 갱신이 0건이면 IllegalStateException을 던진다")
    void increment_for_follow_throws_when_followers_update_returns_zero() {
        // given
        given(followCountRepository.incrementFolloweesCount(1L)).willReturn(1);
        given(followCountRepository.incrementFollowersCount(2L)).willReturn(0);

        // when & then
        assertThatThrownBy(() -> followCountService.incrementForFollow(1L, 2L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("FollowCount");
    }

    @Test
    @DisplayName("decrementForUnfollow는 갱신이 0건이어도 예외를 던지지 않는다")
    void decrement_for_unfollow_does_not_throw_when_update_returns_zero() {
        // given
        given(followCountRepository.decrementFolloweesCount(1L)).willReturn(0);
        given(followCountRepository.decrementFollowersCount(2L)).willReturn(0);

        // when
        followCountService.decrementForUnfollow(1L, 2L);

        // then
        then(followCountRepository).should().decrementFolloweesCount(1L);
        then(followCountRepository).should().decrementFollowersCount(2L);
    }

    @Test
    @DisplayName("getByUserId는 해당 유저의 FollowCount를 반환한다")
    void get_by_user_id_returns_follow_count() {
        // given
        FollowCount followCount = new FollowCount(1L);
        given(followCountRepository.findByUserId(1L)).willReturn(Optional.of(followCount));

        // when
        FollowCount found = followCountService.getByUserId(1L);

        // then
        assertThat(found).isSameAs(followCount);
    }

    @Test
    @DisplayName("getByUserId는 FollowCount가 없으면 IllegalStateException을 던진다")
    void get_by_user_id_throws_when_missing() {
        // given
        given(followCountRepository.findByUserId(1L)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> followCountService.getByUserId(1L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("1");
    }
}
