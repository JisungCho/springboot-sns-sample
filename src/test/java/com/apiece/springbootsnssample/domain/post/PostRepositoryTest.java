package com.apiece.springbootsnssample.domain.post;

import com.apiece.springbootsnssample.config.JpaConfig;
import com.apiece.springbootsnssample.domain.user.User;
import com.apiece.springbootsnssample.domain.user.UserRepository;
import jakarta.persistence.EntityManager;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(JpaConfig.class)
class PostRepositoryTest {

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EntityManager entityManager;

    private User author;

    @BeforeEach
    void setUp() {
        author = userRepository.save(new User("user1", "encoded"));
    }

    private long rowCount(String where) {
        return ((Number) entityManager
                .createNativeQuery("select count(*) from posts where " + where)
                .getSingleResult()).longValue();
    }

    @Test
    @DisplayName("delete는 행을 지우지 않고 deleted_at만 설정하며 조회에서 제외된다")
    void delete_is_soft() {
        // given
        Post post = postRepository.saveAndFlush(Post.createPost(author, "content"));

        // when
        postRepository.delete(post);
        postRepository.flush();

        // then
        assertThat(postRepository.findById(post.getId())).isEmpty();
        assertThat(postRepository.findAll()).isEmpty();
        assertThat(rowCount("deleted_at is not null")).isEqualTo(1);
    }

    @Test
    @DisplayName("삭제된 리포스트는 중복 검사에서 제외되어 재리포스트할 수 있다")
    void deleted_repost_is_not_counted_as_duplicate() {
        // given
        Post original = postRepository.saveAndFlush(Post.createPost(author, "original"));
        Post repost = postRepository.saveAndFlush(Post.createRepost(author, original.getId()));

        // when
        postRepository.delete(repost);
        postRepository.flush();

        // then
        assertThat(postRepository.existsByUserIdAndRepostId(author.getId(), original.getId())).isFalse();
        assertThat(postRepository.findByUserIdAndRepostId(author.getId(), original.getId())).isEmpty();
    }

    @Test
    @DisplayName("활성 리포스트가 있으면 중복 검사에서 true를 반환한다")
    void active_repost_is_detected_as_duplicate() {
        // given
        Post original = postRepository.saveAndFlush(Post.createPost(author, "original"));
        postRepository.saveAndFlush(Post.createRepost(author, original.getId()));

        // then
        assertThat(postRepository.existsByUserIdAndRepostId(author.getId(), original.getId())).isTrue();
    }

    @Test
    @DisplayName("카운트 증가는 원자적 UPDATE로 반영되고 0 미만으로 내려가지 않는다")
    void count_increment_and_decrement() {
        // given
        Post post = postRepository.saveAndFlush(Post.createPost(author, "content"));
        Long postId = post.getId();

        // when
        postRepository.incrementReplyCount(postId);
        postRepository.incrementRepostCount(postId);
        postRepository.incrementViewCount(postId);

        // then
        Post incremented = postRepository.findById(postId).orElseThrow();
        assertThat(incremented.getReplyCount()).isEqualTo(1);
        assertThat(incremented.getRepostCount()).isEqualTo(1);
        assertThat(incremented.getViewCount()).isEqualTo(1);

        // when
        postRepository.decrementReplyCount(postId);
        postRepository.decrementReplyCount(postId);
        postRepository.decrementRepostCount(postId);

        // then
        Post decremented = postRepository.findById(postId).orElseThrow();
        assertThat(decremented.getReplyCount()).isZero();
        assertThat(decremented.getRepostCount()).isZero();
        assertThat(decremented.getLikeCount()).isZero();
    }

    @Test
    @DisplayName("답글은 오래된 순, 인용글과 리포스트는 최신순으로 조회된다")
    void lists_are_ordered() {
        // given
        Post original = postRepository.saveAndFlush(Post.createPost(author, "original"));
        Post firstReply = postRepository.saveAndFlush(Post.createReply(author, "r1", original.getId()));
        Post secondReply = postRepository.saveAndFlush(Post.createReply(author, "r2", original.getId()));
        Post firstQuote = postRepository.saveAndFlush(Post.createQuote(author, "q1", original.getId()));
        Post secondQuote = postRepository.saveAndFlush(Post.createQuote(author, "q2", original.getId()));
        Post firstRepost = postRepository.saveAndFlush(Post.createRepost(author, original.getId()));

        // when
        List<Post> replies = postRepository.findAllByParentIdOrderByCreatedAtAsc(original.getId());
        List<Post> quotes = postRepository.findAllByQuoteIdOrderByCreatedAtDesc(original.getId());
        List<Post> reposts = postRepository.findAllByRepostIdOrderByCreatedAtDesc(original.getId());

        // then
        assertThat(replies).extracting(Post::getId).containsExactly(firstReply.getId(), secondReply.getId());
        assertThat(quotes).extracting(Post::getId).containsExactly(secondQuote.getId(), firstQuote.getId());
        assertThat(reposts).extracting(Post::getId).containsExactly(firstRepost.getId());
        assertThat(replies).allSatisfy(post -> assertThat(post.getUser().getUsername()).isEqualTo("user1"));
    }

    @Test
    @DisplayName("findWithUserById는 작성자를 함께 조회한다")
    void find_with_user_joins_author() {
        // given
        Post post = postRepository.saveAndFlush(Post.createPost(author, "content"));

        // when
        Post found = postRepository.findWithUserById(post.getId()).orElseThrow();

        // then
        assertThat(found.getUser().getUsername()).isEqualTo("user1");
    }
}
