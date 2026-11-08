package com.apiece.springbootsnssample.domain.followcount;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FollowCountRepository extends JpaRepository<FollowCount, Long> {

    Optional<FollowCount> findByUserId(Long userId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update FollowCount fc set fc.followeesCount = fc.followeesCount + 1, fc.updatedAt = CURRENT_TIMESTAMP where fc.userId = :userId")
    int incrementFolloweesCount(@Param("userId") Long userId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update FollowCount fc set fc.followersCount = fc.followersCount + 1, fc.updatedAt = CURRENT_TIMESTAMP where fc.userId = :userId")
    int incrementFollowersCount(@Param("userId") Long userId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update FollowCount fc set fc.followeesCount = fc.followeesCount - 1, fc.updatedAt = CURRENT_TIMESTAMP where fc.userId = :userId and fc.followeesCount > 0")
    int decrementFolloweesCount(@Param("userId") Long userId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update FollowCount fc set fc.followersCount = fc.followersCount - 1, fc.updatedAt = CURRENT_TIMESTAMP where fc.userId = :userId and fc.followersCount > 0")
    int decrementFollowersCount(@Param("userId") Long userId);
}
