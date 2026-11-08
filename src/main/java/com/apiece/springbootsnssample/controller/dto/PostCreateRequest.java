package com.apiece.springbootsnssample.controller.dto;

import com.apiece.springbootsnssample.domain.post.Post;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 게시글/답글/인용글 작성 요청 DTO.
 *
 * <p>리포스트는 본문이 없으므로 이 DTO를 쓰지 않는다.
 *
 * @param content 본문 (필수, 500자 이하)
 */
public record PostCreateRequest(
        @NotBlank(message = "본문은 필수입니다.")
        @Size(max = Post.MAX_CONTENT_LENGTH, message = "본문은 500자 이하로 입력해주세요.")
        String content
) {
}
