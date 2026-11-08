package com.apiece.springbootsnssample.controller.dto;

import com.apiece.springbootsnssample.domain.user.User;

public record FollowUserResponse(Long id, String username) {

    public static FollowUserResponse from(User user) {
        return new FollowUserResponse(user.getId(), user.getUsername());
    }
}
