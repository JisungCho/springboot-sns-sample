package com.apiece.springbootsnssample.domain.followcount;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class FollowCountService {

    private final FollowCountRepository followCountRepository;

    public FollowCount create(Long userId) {
        return followCountRepository.save(new FollowCount(userId));
    }

    @Transactional
    public void incrementForFollow(Long followerId, Long followeeId) {
        int followeesUpdated = followCountRepository.incrementFolloweesCount(followerId);
        int followersUpdated = followCountRepository.incrementFollowersCount(followeeId);
        if (followeesUpdated == 0 || followersUpdated == 0) {
            throw new IllegalStateException("FollowCount가 존재하지 않습니다.");
        }
    }

    @Transactional
    public void decrementForUnfollow(Long followerId, Long followeeId) {
        followCountRepository.decrementFolloweesCount(followerId);
        followCountRepository.decrementFollowersCount(followeeId);
    }

    public FollowCount getByUserId(Long userId) {
        return followCountRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalStateException("FollowCount가 존재하지 않습니다: " + userId));
    }
}
