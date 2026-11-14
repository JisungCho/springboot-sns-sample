package com.apiece.springbootsnssample.domain.like;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LikeRepository extends JpaRepository<Like, Long> {

    boolean existsByUserIdAndPostId(Long userId, Long postId);

    List<Like> findAllByUserIdOrderByCreatedAtDesc(Long userId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update PostLike l set l.deletedAt = CURRENT_TIMESTAMP, l.updatedAt = CURRENT_TIMESTAMP "
            + "where l.userId = :userId and l.postId = :postId and l.deletedAt is null")
    int deleteByUserIdAndPostId(@Param("userId") Long userId, @Param("postId") Long postId);
}
