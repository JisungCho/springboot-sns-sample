package com.apiece.springbootsnssample.controller;

import com.apiece.springbootsnssample.controller.dto.PostCreateRequest;
import com.apiece.springbootsnssample.controller.dto.PostResponse;
import com.apiece.springbootsnssample.domain.post.Post;
import com.apiece.springbootsnssample.domain.post.PostService;
import com.apiece.springbootsnssample.domain.user.UserPrincipal;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class PostController {

    private final PostService postService;

    @PostMapping("/api/v1/posts")
    public ResponseEntity<PostResponse> createPost(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody PostCreateRequest request) {
        Post post = postService.createPost(principal.getUser().getId(), request.content());
        return ResponseEntity.status(HttpStatus.CREATED).body(PostResponse.from(post));
    }

    @GetMapping("/api/v1/posts/{postId}")
    public ResponseEntity<PostResponse> getPost(@PathVariable Long postId) {
        return ResponseEntity.ok(PostResponse.from(postService.getPost(postId)));
    }

    @DeleteMapping("/api/v1/posts/{postId}")
    public ResponseEntity<Void> deletePost(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long postId) {
        postService.deletePost(principal.getUser().getId(), postId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/v1/posts/{postId}/replies")
    public ResponseEntity<PostResponse> createReply(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long postId,
            @Valid @RequestBody PostCreateRequest request) {
        Post reply = postService.createReply(principal.getUser().getId(), postId, request.content());
        return ResponseEntity.status(HttpStatus.CREATED).body(PostResponse.from(reply));
    }

    @GetMapping("/api/v1/posts/{postId}/replies")
    public ResponseEntity<List<PostResponse>> getReplies(@PathVariable Long postId) {
        return ResponseEntity.ok(toResponses(postService.getReplies(postId)));
    }

    @PostMapping("/api/v1/posts/{postId}/quotes")
    public ResponseEntity<PostResponse> createQuote(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long postId,
            @Valid @RequestBody PostCreateRequest request) {
        Post quote = postService.createQuote(principal.getUser().getId(), postId, request.content());
        return ResponseEntity.status(HttpStatus.CREATED).body(PostResponse.from(quote));
    }

    @GetMapping("/api/v1/posts/{postId}/quotes")
    public ResponseEntity<List<PostResponse>> getQuotes(@PathVariable Long postId) {
        return ResponseEntity.ok(toResponses(postService.getQuotes(postId)));
    }

    @PostMapping("/api/v1/posts/{postId}/reposts")
    public ResponseEntity<PostResponse> createRepost(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long postId) {
        Post repost = postService.createRepost(principal.getUser().getId(), postId);
        return ResponseEntity.status(HttpStatus.CREATED).body(PostResponse.from(repost));
    }

    @DeleteMapping("/api/v1/posts/{postId}/reposts")
    public ResponseEntity<Void> cancelRepost(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long postId) {
        postService.cancelRepost(principal.getUser().getId(), postId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/v1/posts/{postId}/reposts")
    public ResponseEntity<List<PostResponse>> getReposts(@PathVariable Long postId) {
        return ResponseEntity.ok(toResponses(postService.getReposts(postId)));
    }

    private static List<PostResponse> toResponses(List<Post> posts) {
        return posts.stream()
                .map(PostResponse::from)
                .toList();
    }
}
