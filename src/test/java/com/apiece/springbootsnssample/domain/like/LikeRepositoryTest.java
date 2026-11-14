package com.apiece.springbootsnssample.domain.like;

import com.apiece.springbootsnssample.config.JpaConfig;
import com.apiece.springbootsnssample.domain.post.Post;
import com.apiece.springbootsnssample.domain.post.PostRepository;
import com.apiece.springbootsnssample.domain.user.User;
import com.apiece.springbootsnssample.domain.user.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(JpaConfig.class)
class LikeRepositoryTest {

    @Autowired
    private LikeRepository likeRepository;

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EntityManager entityManager;

    private User author;
    private Post post;

    @BeforeEach
    void setUp() {
        author = userRepository.save(new User("user1", "encoded"));
        post = postRepository.saveAndFlush(Post.createPost(author, "content"));
    }

    private long rowCount(String where) {
        return ((Number) entityManager
                .createNativeQuery("select count(*) from likes where " + where)
                .getSingleResult()).longValue();
    }

    @Test
    @DisplayName("deleteByUserIdAndPostId는 행을 지우지 않고 deleted_at만 설정하며 조회에서 제외된다")
    void bulk_delete_is_soft() {
        // given
        likeRepository.saveAndFlush(new Like(author.getId(), post.getId()));

        // when
        int deleted = likeRepository.deleteByUserIdAndPostId(author.getId(), post.getId());

        // then
        assertThat(deleted).isEqualTo(1);
        assertThat(likeRepository.existsByUserIdAndPostId(author.getId(), post.getId())).isFalse();
        assertThat(likeRepository.findAllByUserIdOrderByCreatedAtDesc(author.getId())).isEmpty();
        assertThat(rowCount("deleted_at is not null")).isEqualTo(1);
    }

    @Test
    @DisplayName("이미 취소한 좋아요를 다시 취소하면 0건이 갱신된다")
    void delete_twice_updates_nothing() {
        // given
        likeRepository.saveAndFlush(new Like(author.getId(), post.getId()));
        likeRepository.deleteByUserIdAndPostId(author.getId(), post.getId());

        // when
        int deleted = likeRepository.deleteByUserIdAndPostId(author.getId(), post.getId());

        // then
        assertThat(deleted).isZero();
    }

    @Test
    @DisplayName("소프트 삭제된 좋아요는 중복 검사에서 제외되어 다시 좋아요할 수 있다")
    void deleted_like_is_not_counted_as_duplicate() {
        // given
        Like like = likeRepository.saveAndFlush(new Like(author.getId(), post.getId()));

        // when
        likeRepository.delete(like);
        likeRepository.flush();

        // then
        assertThat(likeRepository.existsByUserIdAndPostId(author.getId(), post.getId())).isFalse();
        assertThat(rowCount("deleted_at is not null")).isEqualTo(1);
    }

    @Test
    @DisplayName("JPA delete도 소프트 삭제로 동작한다")
    void entity_delete_is_soft() {
        // given
        Like saved = likeRepository.saveAndFlush(new Like(author.getId(), post.getId()));

        // when
        likeRepository.delete(saved);
        likeRepository.flush();

        // then
        assertThat(likeRepository.findAll()).isEmpty();
        assertThat(rowCount("deleted_at is not null")).isEqualTo(1);
    }

    @Test
    @DisplayName("likeCount는 원자적 UPDATE로 증감하고 0 미만으로 내려가지 않는다")
    void like_count_increment_and_decrement() {
        // given
        Long postId = post.getId();

        // when
        postRepository.incrementLikeCount(postId);
        postRepository.incrementLikeCount(postId);

        // then
        assertThat(postRepository.findLikeCountById(postId)).contains(2);

        // when
        postRepository.decrementLikeCount(postId);
        postRepository.decrementLikeCount(postId);
        postRepository.decrementLikeCount(postId);

        // then
        assertThat(postRepository.findLikeCountById(postId)).contains(0);
    }
}
