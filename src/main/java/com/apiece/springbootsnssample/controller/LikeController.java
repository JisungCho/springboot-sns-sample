package com.apiece.springbootsnssample.controller;

import com.apiece.springbootsnssample.controller.dto.LikeResponse;
import com.apiece.springbootsnssample.controller.dto.PostResponse;
import com.apiece.springbootsnssample.domain.like.LikeResult;
import com.apiece.springbootsnssample.domain.like.LikeService;
import com.apiece.springbootsnssample.domain.post.Post;
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
public class LikeController {

    private final LikeService likeService;

    @PostMapping("/api/v1/posts/{postId}/likes")
    public ResponseEntity<LikeResponse> like(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long postId) {
        LikeResult result = likeService.like(principal.getUser().getId(), postId);
        return ResponseEntity.status(HttpStatus.CREATED).body(LikeResponse.from(result));
    }

    @DeleteMapping("/api/v1/posts/{postId}/likes")
    public ResponseEntity<Void> unlike(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long postId) {
        likeService.unlike(principal.getUser().getId(), postId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/v1/liked-posts")
    public ResponseEntity<List<PostResponse>> getLikedPosts(
            @AuthenticationPrincipal UserPrincipal principal) {
        List<PostResponse> posts = likeService.getLikedPosts(principal.getUser().getId()).stream()
                .map(PostResponse::from)
                .toList();
        return ResponseEntity.ok(posts);
    }
}
