package com.apiece.springbootsnssample.domain.like;

import org.springframework.http.HttpStatus;

public class LikeException extends RuntimeException {

    private final HttpStatus status;

    public LikeException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
