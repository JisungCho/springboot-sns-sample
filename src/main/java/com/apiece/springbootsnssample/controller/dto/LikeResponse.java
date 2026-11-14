package com.apiece.springbootsnssample.controller.dto;

import com.apiece.springbootsnssample.domain.like.LikeResult;

/**
 * 좋아요 응답 DTO. 좋아요 직후 클라이언트가 다시 조회하지 않아도 되도록 게시글의 현재 likeCount를 함께 담는다.
 */
public record LikeResponse(Long id, Long userId, Long postId, int likeCount) {

    public static LikeResponse from(LikeResult result) {
        return new LikeResponse(result.id(), result.userId(), result.postId(), result.likeCount());
    }
}
