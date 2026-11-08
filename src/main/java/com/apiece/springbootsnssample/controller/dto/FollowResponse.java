package com.apiece.springbootsnssample.controller.dto;

import com.apiece.springbootsnssample.domain.follow.Follow;

public record FollowResponse(Long id, Long followerId, Long followeeId) {

    public static FollowResponse from(Follow follow) {
        return new FollowResponse(follow.getId(), follow.getFollowerId(), follow.getFolloweeId());
    }
}
