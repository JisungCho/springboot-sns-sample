package com.apiece.springbootsnssample.domain.follow;

import com.apiece.springbootsnssample.config.JpaConfig;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(JpaConfig.class)
class FollowRepositoryTest {

    @Autowired
    private FollowRepository followRepository;

    @Autowired
    private EntityManager entityManager;

    private long rowCount(String where) {
        return ((Number) entityManager
                .createNativeQuery("select count(*) from follows where " + where)
                .getSingleResult()).longValue();
    }

    @Test
    @DisplayName("deleteByFollowerIdAndFolloweeId는 행을 지우지 않고 deleted_at만 설정하며 조회에서 제외된다")
    void bulk_delete_is_soft() {
        // given
        followRepository.saveAndFlush(new Follow(1L, 2L));

        // when
        int deleted = followRepository.deleteByFollowerIdAndFolloweeId(1L, 2L);

        // then
        assertThat(deleted).isEqualTo(1);
        assertThat(followRepository.existsByFollowerIdAndFolloweeId(1L, 2L)).isFalse();
        assertThat(followRepository.findAllByFollowerIdOrderByCreatedAtDesc(1L)).isEmpty();
        assertThat(rowCount("follower_id = 1 and followee_id = 2 and deleted_at is not null")).isEqualTo(1);
    }

    @Test
    @DisplayName("이미 삭제된 팔로우를 다시 삭제하면 0건이 갱신된다")
    void delete_twice_updates_nothing() {
        // given
        followRepository.saveAndFlush(new Follow(1L, 2L));
        followRepository.deleteByFollowerIdAndFolloweeId(1L, 2L);

        // when
        int deleted = followRepository.deleteByFollowerIdAndFolloweeId(1L, 2L);

        // then
        assertThat(deleted).isZero();
    }

    @Test
    @DisplayName("JPA delete/deleteAll도 소프트 삭제로 동작한다")
    void entity_delete_is_soft() {
        // given
        Follow saved = followRepository.saveAndFlush(new Follow(1L, 2L));

        // when
        followRepository.delete(saved);
        followRepository.flush();

        // then
        assertThat(followRepository.findAll()).isEmpty();
        assertThat(rowCount("deleted_at is not null")).isEqualTo(1);
    }
}
