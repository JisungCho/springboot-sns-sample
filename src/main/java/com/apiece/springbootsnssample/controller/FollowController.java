package com.apiece.springbootsnssample.controller;

import com.apiece.springbootsnssample.controller.dto.FollowCountResponse;
import com.apiece.springbootsnssample.controller.dto.FollowResponse;
import com.apiece.springbootsnssample.controller.dto.FollowUserResponse;
import com.apiece.springbootsnssample.domain.follow.Follow;
import com.apiece.springbootsnssample.domain.follow.FollowService;
import com.apiece.springbootsnssample.domain.followcount.FollowCountService;
import com.apiece.springbootsnssample.domain.user.UserPrincipal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class FollowController {

    private final FollowService followService;
    private final FollowCountService followCountService;

    @PostMapping("/api/v1/follow/{followeeId}")
    public ResponseEntity<FollowResponse> follow(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long followeeId) {
        Follow follow = followService.follow(principal.getUser().getId(), followeeId);
        return ResponseEntity.status(HttpStatus.CREATED).body(FollowResponse.from(follow));
    }

    @DeleteMapping("/api/v1/follow/{followeeId}")
    public ResponseEntity<Void> unfollow(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long followeeId) {
        followService.unfollow(principal.getUser().getId(), followeeId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/v1/followers")
    public ResponseEntity<List<FollowUserResponse>> getFollowers(
            @AuthenticationPrincipal UserPrincipal principal) {
        List<FollowUserResponse> followers = followService.getFollowers(principal.getUser().getId()).stream()
                .map(FollowUserResponse::from)
                .toList();
        return ResponseEntity.ok(followers);
    }

    @GetMapping("/api/v1/followees")
    public ResponseEntity<List<FollowUserResponse>> getFollowees(
            @AuthenticationPrincipal UserPrincipal principal) {
        List<FollowUserResponse> followees = followService.getFollowees(principal.getUser().getId()).stream()
                .map(FollowUserResponse::from)
                .toList();
        return ResponseEntity.ok(followees);
    }

    @GetMapping("/api/v1/follow-count")
    public ResponseEntity<FollowCountResponse> getFollowCount(
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(
                FollowCountResponse.from(followCountService.getByUserId(principal.getUser().getId())));
    }
}
