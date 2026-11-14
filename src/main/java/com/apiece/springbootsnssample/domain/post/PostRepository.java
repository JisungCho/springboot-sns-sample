package com.apiece.springbootsnssample.domain.post;

import java.util.Collection;
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

    // 조회수는 Redis에 모아 두었다가 스케줄러가 한 번에 반영하므로 증가량을 인자로 받는다.
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Post p set p.viewCount = p.viewCount + :delta, p.updatedAt = CURRENT_TIMESTAMP where p.id = :postId")
    int incrementViewCountBy(@Param("postId") Long postId, @Param("delta") long delta);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Post p set p.likeCount = p.likeCount + 1, p.updatedAt = CURRENT_TIMESTAMP where p.id = :postId")
    int incrementLikeCount(@Param("postId") Long postId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Post p set p.likeCount = p.likeCount - 1, p.updatedAt = CURRENT_TIMESTAMP "
            + "where p.id = :postId and p.likeCount > 0")
    int decrementLikeCount(@Param("postId") Long postId);

    @Query("select p.likeCount from Post p where p.id = :postId")
    Optional<Integer> findLikeCountById(@Param("postId") Long postId);

    // 내가 좋아요한 게시글 목록. 좋아요 순서(최신순)는 서비스에서 유지한다.
    @EntityGraph(attributePaths = "user")
    List<Post> findAllByIdIn(Collection<Long> ids);
}
