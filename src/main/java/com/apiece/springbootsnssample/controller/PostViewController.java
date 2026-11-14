package com.apiece.springbootsnssample.controller;

import com.apiece.springbootsnssample.domain.post.PostViewCountService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 게시글 조회수 집계 API.
 *
 * <p>조회수는 Redis에만 반영하고, DB의 {@code view_count}는 스케줄러가 주기적으로 따라잡는다.
 */
@RestController
@RequiredArgsConstructor
public class PostViewController {

    private final PostViewCountService postViewCountService;

    @PostMapping("/api/v1/posts/{postId}/views")
    public ResponseEntity<Void> increaseViewCount(@PathVariable Long postId) {
        postViewCountService.increaseViewCount(postId);
        return ResponseEntity.noContent().build();
    }
}
