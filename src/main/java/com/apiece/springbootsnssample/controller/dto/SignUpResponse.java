package com.apiece.springbootsnssample.controller.dto;

import com.apiece.springbootsnssample.domain.user.User;

public record SignUpResponse(Long id, String username) {

    public static SignUpResponse from(User user) {
        return new SignUpResponse(user.getId(), user.getUsername());
    }
}
