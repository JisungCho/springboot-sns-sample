package com.apiece.springbootsnssample.controller.dto;

import com.apiece.springbootsnssample.domain.post.Post;
import java.time.LocalDateTime;

/**
 * 게시글 응답 DTO.
 *
 * <p>리포스트는 {@code repostId}만 채워지고 나머지 본문 관련 값은 비어 있다. 원문 내용은 클라이언트가
 * {@code repostId}로 별도 조회한다.
 */
public record PostResponse(
        Long id,
        String content,
        Long authorId,
        String authorUsername,
        Long parentId,
        Long quoteId,
        Long repostId,
        int replyCount,
        int repostCount,
        int likeCount,
        long viewCount,
        LocalDateTime createdAt
) {

    public static PostResponse from(Post post) {
        return new PostResponse(
                post.getId(),
                post.getContent(),
                post.getUser().getId(),
                post.getUser().getUsername(),
                post.getParentId(),
                post.getQuoteId(),
                post.getRepostId(),
                post.getReplyCount(),
                post.getRepostCount(),
                post.getLikeCount(),
                post.getViewCount(),
                post.getCreatedAt());
    }
}
