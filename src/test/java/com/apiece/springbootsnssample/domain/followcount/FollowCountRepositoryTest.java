package com.apiece.springbootsnssample.domain.followcount;

import com.apiece.springbootsnssample.config.JpaConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(JpaConfig.class)
class FollowCountRepositoryTest {

    @Autowired
    private FollowCountRepository followCountRepository;

    @Test
    @DisplayName("incrementFolloweesCount와 incrementFollowersCount는 카운트를 1씩 증가시킨다")
    void increment_updates_counts() {
        // given
        followCountRepository.save(new FollowCount(1L));

        // when
        int followeesUpdated = followCountRepository.incrementFolloweesCount(1L);
        int followersUpdated = followCountRepository.incrementFollowersCount(1L);

        // then
        FollowCount reloaded = followCountRepository.findByUserId(1L).orElseThrow();
        assertThat(followeesUpdated).isEqualTo(1);
        assertThat(followersUpdated).isEqualTo(1);
        assertThat(reloaded.getFolloweesCount()).isEqualTo(1L);
        assertThat(reloaded.getFollowersCount()).isEqualTo(1L);
    }

    @Test
    @DisplayName("decrementFolloweesCount와 decrementFollowersCount는 카운트를 1씩 감소시킨다")
    void decrement_updates_counts() {
        // given
        followCountRepository.save(FollowCount.builder()
                .userId(1L)
                .followersCount(2L)
                .followeesCount(3L)
                .build());

        // when
        int followeesUpdated = followCountRepository.decrementFolloweesCount(1L);
        int followersUpdated = followCountRepository.decrementFollowersCount(1L);

        // then
        FollowCount reloaded = followCountRepository.findByUserId(1L).orElseThrow();
        assertThat(followeesUpdated).isEqualTo(1);
        assertThat(followersUpdated).isEqualTo(1);
        assertThat(reloaded.getFolloweesCount()).isEqualTo(2L);
        assertThat(reloaded.getFollowersCount()).isEqualTo(1L);
    }

    @Test
    @DisplayName("카운트가 0일 때 decrement해도 0으로 유지된다")
    void decrement_at_zero_stays_zero() {
        // given
        followCountRepository.save(new FollowCount(1L));

        // when
        int followeesUpdated = followCountRepository.decrementFolloweesCount(1L);
        int followersUpdated = followCountRepository.decrementFollowersCount(1L);

        // then
        FollowCount reloaded = followCountRepository.findByUserId(1L).orElseThrow();
        assertThat(followeesUpdated).isZero();
        assertThat(followersUpdated).isZero();
        assertThat(reloaded.getFolloweesCount()).isZero();
        assertThat(reloaded.getFollowersCount()).isZero();
    }
}
