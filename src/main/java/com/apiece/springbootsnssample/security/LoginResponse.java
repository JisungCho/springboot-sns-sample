package com.apiece.springbootsnssample.security;

import org.springframework.security.core.Authentication;

public record LoginResponse(String username) {

    public static LoginResponse from(Authentication authentication) {
        return new LoginResponse(authentication.getName());
    }
}
