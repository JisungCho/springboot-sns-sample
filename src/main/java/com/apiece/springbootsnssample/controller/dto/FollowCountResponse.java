package com.apiece.springbootsnssample.controller.dto;

import com.apiece.springbootsnssample.domain.followcount.FollowCount;

public record FollowCountResponse(long followersCount, long followeesCount) {

    public static FollowCountResponse from(FollowCount followCount) {
        return new FollowCountResponse(followCount.getFollowersCount(), followCount.getFolloweesCount());
    }
}
