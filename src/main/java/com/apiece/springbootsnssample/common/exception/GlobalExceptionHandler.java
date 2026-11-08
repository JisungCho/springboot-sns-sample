package com.apiece.springbootsnssample.common.exception;

import com.apiece.springbootsnssample.domain.follow.FollowException;
import com.apiece.springbootsnssample.domain.post.PostException;
import com.apiece.springbootsnssample.domain.user.UserException;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(UserException.class)
    public ResponseEntity<ErrorResponse> handleUserException(UserException e) {
        return ResponseEntity.status(e.getStatus()).body(ErrorResponse.from(e.getMessage()));
    }

    @ExceptionHandler(FollowException.class)
    public ResponseEntity<ErrorResponse> handleFollowException(FollowException e) {
        return ResponseEntity.status(e.getStatus()).body(ErrorResponse.from(e.getMessage()));
    }

    @ExceptionHandler(PostException.class)
    public ResponseEntity<ErrorResponse> handlePostException(PostException e) {
        return ResponseEntity.status(e.getStatus()).body(ErrorResponse.from(e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(FieldError::getDefaultMessage)
                .orElse("요청 값이 유효하지 않습니다.");
        return ResponseEntity.badRequest().body(ErrorResponse.from(message));
    }
}
