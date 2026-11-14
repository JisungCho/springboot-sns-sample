package com.apiece.springbootsnssample.domain.like;

/**
 * 좋아요 처리 결과. 좋아요 저장과 같은 트랜잭션에서 확정한 게시글의 likeCount를 함께 담는다.
 *
 * <p>컨트롤러가 좋아요 트랜잭션이 끝난 뒤 별도로 카운트를 조회하면, 그 사이 게시글이 삭제되거나 다른
 * 사용자의 좋아요가 끼어들어 응답 값이 어긋날 수 있다. 그래서 카운트는 서비스가 같은 트랜잭션 안에서
 * 확정해 이 결과로 돌려준다.
 */
public record LikeResult(Long id, Long userId, Long postId, int likeCount) {
}
