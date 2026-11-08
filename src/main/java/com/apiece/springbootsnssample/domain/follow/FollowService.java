package com.apiece.springbootsnssample.domain.follow;

import com.apiece.springbootsnssample.domain.followcount.FollowCountService;
import com.apiece.springbootsnssample.domain.user.User;
import com.apiece.springbootsnssample.domain.user.UserRepository;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class FollowService {

    private final FollowRepository followRepository;
    private final UserRepository userRepository;
    private final FollowCountService followCountService;

    @Transactional
    public Follow follow(Long followerId, Long followeeId) {
        if (followerId.equals(followeeId)) {
            throw new FollowException(HttpStatus.BAD_REQUEST, "자기 자신을 팔로우할 수 없습니다.");
        }
        if (!userRepository.existsById(followeeId)) {
            throw new FollowException(HttpStatus.NOT_FOUND, "존재하지 않는 사용자입니다.");
        }
        if (followRepository.existsByFollowerIdAndFolloweeId(followerId, followeeId)) {
            throw new FollowException(HttpStatus.CONFLICT, "이미 팔로우한 사용자입니다.");
        }
        try {
            Follow saved = followRepository.save(new Follow(followerId, followeeId));
            followCountService.incrementForFollow(followerId, followeeId);
            return saved;
        } catch (DataIntegrityViolationException e) {
            throw new FollowException(HttpStatus.CONFLICT, "이미 팔로우한 사용자입니다.");
        }
    }

    @Transactional
    public void unfollow(Long followerId, Long followeeId) {
        int deleted = followRepository.deleteByFollowerIdAndFolloweeId(followerId, followeeId);
        if (deleted == 0) {
            throw new FollowException(HttpStatus.NOT_FOUND, "팔로우 관계가 존재하지 않습니다.");
        }
        followCountService.decrementForUnfollow(followerId, followeeId);
    }

    public List<User> getFollowees(Long followerId) {
        List<Follow> follows = followRepository.findAllByFollowerIdOrderByCreatedAtDesc(followerId);
        List<Long> followeeIds = follows.stream()
                .map(Follow::getFolloweeId)
                .toList();
        return mapUsersPreservingOrder(followeeIds);
    }

    public List<User> getFollowers(Long followeeId) {
        List<Follow> follows = followRepository.findAllByFolloweeIdOrderByCreatedAtDesc(followeeId);
        List<Long> followerIds = follows.stream()
                .map(Follow::getFollowerId)
                .toList();
        return mapUsersPreservingOrder(followerIds);
    }

    private List<User> mapUsersPreservingOrder(List<Long> userIds) {
        Map<Long, User> usersById = userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        return userIds.stream()
                .map(usersById::get)
                .filter(Objects::nonNull)
                .toList();
    }
}
