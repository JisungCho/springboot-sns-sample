package com.apiece.springbootsnssample.domain.follow;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FollowRepository extends JpaRepository<Follow, Long> {

    boolean existsByFollowerIdAndFolloweeId(Long followerId, Long followeeId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Follow f set f.deletedAt = CURRENT_TIMESTAMP, f.updatedAt = CURRENT_TIMESTAMP "
            + "where f.followerId = :followerId and f.followeeId = :followeeId and f.deletedAt is null")
    int deleteByFollowerIdAndFolloweeId(
            @Param("followerId") Long followerId, @Param("followeeId") Long followeeId);

    List<Follow> findAllByFollowerIdOrderByCreatedAtDesc(Long followerId);

    List<Follow> findAllByFolloweeIdOrderByCreatedAtDesc(Long followeeId);
}
