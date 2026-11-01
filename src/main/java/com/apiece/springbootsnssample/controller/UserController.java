package com.apiece.springbootsnssample.controller;

import com.apiece.springbootsnssample.controller.dto.SignUpRequest;
import com.apiece.springbootsnssample.controller.dto.SignUpResponse;
import com.apiece.springbootsnssample.domain.user.User;
import com.apiece.springbootsnssample.domain.user.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PostMapping("/api/v1/users/signup")
    public ResponseEntity<SignUpResponse> signup(@Valid @RequestBody SignUpRequest request) {
        User user = userService.signup(request.toEntity());
        return ResponseEntity.status(HttpStatus.CREATED).body(SignUpResponse.from(user));
    }
}
