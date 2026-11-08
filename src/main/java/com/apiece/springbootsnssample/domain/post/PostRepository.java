package com.apiece.springbootsnssample.domain.post;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PostRepository extends JpaRepository<Post, Long> {

    @EntityGraph(attributePaths = "user")
    Optional<Post> findWithUserById(Long id);

    boolean existsByUserIdAndRepostId(Long userId, Long repostId);

    Optional<Post> findByUserIdAndRepostId(Long userId, Long repostId);

    @EntityGraph(attributePaths = "user")
    List<Post> findAllByParentIdOrderByCreatedAtAsc(Long parentId);

    @EntityGraph(attributePaths = "user")
    List<Post> findAllByQuoteIdOrderByCreatedAtDesc(Long quoteId);

    @EntityGraph(attributePaths = "user")
    List<Post> findAllByRepostIdOrderByCreatedAtDesc(Long repostId);

    // @SQLRestriction("deleted_at is null") 이 이미 붙으므로 deleted_at 조건은 중복해서 쓰지 않는다.
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Post p set p.replyCount = p.replyCount + 1, p.updatedAt = CURRENT_TIMESTAMP where p.id = :postId")
    int incrementReplyCount(@Param("postId") Long postId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Post p set p.replyCount = p.replyCount - 1, p.updatedAt = CURRENT_TIMESTAMP "
            + "where p.id = :postId and p.replyCount > 0")
    int decrementReplyCount(@Param("postId") Long postId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Post p set p.repostCount = p.repostCount + 1, p.updatedAt = CURRENT_TIMESTAMP where p.id = :postId")
    int incrementRepostCount(@Param("postId") Long postId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Post p set p.repostCount = p.repostCount - 1, p.updatedAt = CURRENT_TIMESTAMP "
            + "where p.id = :postId and p.repostCount > 0")
    int decrementRepostCount(@Param("postId") Long postId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Post p set p.viewCount = p.viewCount + 1, p.updatedAt = CURRENT_TIMESTAMP where p.id = :postId")
    int incrementViewCount(@Param("postId") Long postId);
}
