package com.apiece.springbootsnssample.domain.follow;

import org.springframework.http.HttpStatus;

public class FollowException extends RuntimeException {

    private final HttpStatus status;

    public FollowException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
