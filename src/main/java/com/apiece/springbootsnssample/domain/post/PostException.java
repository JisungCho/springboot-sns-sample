package com.apiece.springbootsnssample.domain.post;

import org.springframework.http.HttpStatus;

public class PostException extends RuntimeException {

    private final HttpStatus status;

    public PostException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
