package com.apiece.springbootsnssample.domain.like;

import com.apiece.springbootsnssample.domain.post.Post;
import com.apiece.springbootsnssample.domain.post.PostRepository;
import com.apiece.springbootsnssample.domain.post.PostService;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LikeService {

    private final LikeRepository likeRepository;
    private final PostRepository postRepository;
    private final PostService postService;

    /**
     * 좋아요를 저장하고 대상 게시글의 likeCount를 함께 올린다.
     *
     * <p>리포스트를 대상으로 하면 원본 글에 좋아요가 달린다.
     *
     * <p>중복은 선검사로 걸러내고, 경합으로 선검사를 통과한 뒤 유니크 인덱스에 걸리는 경우는
     * {@link DataIntegrityViolationException}을 CONFLICT로 변환해 처리한다. 이때 참여한 트랜잭션은
     * 이미 rollback-only로 마킹되므로 catch 블록 안에서는 추가 DB 작업을 하지 않고 예외만 변환한다.
     * (FollowService.follow 와 같은 방식이다.)
     */
    @Transactional
    public LikeResult like(Long userId, Long postId) {
        Post target = postService.getNormalizedTarget(postId);
        if (likeRepository.existsByUserIdAndPostId(userId, target.getId())) {
            throw new LikeException(HttpStatus.CONFLICT, "이미 좋아요한 게시글입니다.");
        }
        try {
            Like saved = likeRepository.save(new Like(userId, target.getId()));
            postRepository.incrementLikeCount(target.getId());
            int likeCount = getLikeCount(target.getId());
            return new LikeResult(saved.getId(), saved.getUserId(), saved.getPostId(), likeCount);
        } catch (DataIntegrityViolationException e) {
            throw new LikeException(HttpStatus.CONFLICT, "이미 좋아요한 게시글입니다.");
        }
    }

    @Transactional
    public void unlike(Long userId, Long postId) {
        Post target = postService.getNormalizedTarget(postId);
        if (likeRepository.deleteByUserIdAndPostId(userId, target.getId()) == 0) {
            throw new LikeException(HttpStatus.NOT_FOUND, "좋아요한 게시글이 아닙니다.");
        }
        postRepository.decrementLikeCount(target.getId());
    }

    /**
     * 내가 좋아요한 게시글을 최근 좋아요 순으로 반환한다.
     *
     * <p>조회 사이에 삭제된 게시글은 결과에서 빠지므로 순서만 좋아요 기록에서 가져온다.
     */
    public List<Post> getLikedPosts(Long userId) {
        List<Long> postIds = likeRepository.findAllByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(Like::getPostId)
                .distinct()
                .toList();
        if (postIds.isEmpty()) {
            return List.of();
        }
        Map<Long, Post> postsById = postRepository.findAllByIdIn(postIds).stream()
                .collect(Collectors.toMap(Post::getId, Function.identity()));
        return postIds.stream()
                .map(postsById::get)
                .filter(Objects::nonNull)
                .toList();
    }

    /**
     * 좋아요 트랜잭션 안에서 대상 게시글의 현재 likeCount를 읽는다.
     *
     * <p>좋아요를 저장한 뒤 게시글이 삭제되었다면 조회 결과가 없으므로 NOT_FOUND로 롤백시킨다.
     */
    private int getLikeCount(Long postId) {
        return postRepository.findLikeCountById(postId)
                .orElseThrow(() -> new LikeException(HttpStatus.NOT_FOUND, "존재하지 않는 게시글입니다."));
    }
}
