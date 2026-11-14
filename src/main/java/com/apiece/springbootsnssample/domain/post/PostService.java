package com.apiece.springbootsnssample.domain.post;

import com.apiece.springbootsnssample.domain.user.User;
import com.apiece.springbootsnssample.domain.user.UserRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PostService {

    private final PostRepository postRepository;
    private final UserRepository userRepository;

    @Transactional
    public Post createPost(Long userId, String content) {
        String validatedContent = validateContent(content);
        User user = getUser(userId);
        return postRepository.save(Post.createPost(user, validatedContent));
    }

    @Transactional
    public Post createReply(Long userId, Long postId, String content) {
        String validatedContent = validateContent(content);
        User user = getUser(userId);
        Post target = getNormalizedTarget(postId);
        Post saved = postRepository.save(Post.createReply(user, validatedContent, target.getId()));
        postRepository.incrementReplyCount(target.getId());
        return saved;
    }

    @Transactional
    public Post createQuote(Long userId, Long postId, String content) {
        String validatedContent = validateContent(content);
        User user = getUser(userId);
        Post target = getNormalizedTarget(postId);
        Post saved = postRepository.save(Post.createQuote(user, validatedContent, target.getId()));
        postRepository.incrementReplyCount(target.getId());
        return saved;
    }

    @Transactional
    public Post createRepost(Long userId, Long postId) {
        User user = getUser(userId);
        Post target = getNormalizedTarget(postId);
        if (postRepository.existsByUserIdAndRepostId(userId, target.getId())) {
            throw new PostException(HttpStatus.CONFLICT, "이미 리포스트한 게시글입니다.");
        }
        try {
            Post saved = postRepository.save(Post.createRepost(user, target.getId()));
            postRepository.incrementRepostCount(target.getId());
            return saved;
        } catch (DataIntegrityViolationException e) {
            throw new PostException(HttpStatus.CONFLICT, "이미 리포스트한 게시글입니다.");
        }
    }

    @Transactional
    public void cancelRepost(Long userId, Long postId) {
        Post target = getNormalizedTarget(postId);
        Post repost = postRepository.findByUserIdAndRepostId(userId, target.getId())
                .orElseThrow(() -> new PostException(HttpStatus.NOT_FOUND, "리포스트한 게시글이 아닙니다."));
        postRepository.delete(repost);
        postRepository.decrementRepostCount(target.getId());
    }

    /**
     * 게시글 단건을 조회한다.
     *
     * <p>조회수 증가는 DB를 직접 갱신하지 않고 {@link PostViewCountService}가 Redis에 기록한다.
     */
    public Post getPost(Long postId) {
        return postRepository.findWithUserById(postId)
                .orElseThrow(() -> new PostException(HttpStatus.NOT_FOUND, "존재하지 않는 게시글입니다."));
    }

    @Transactional
    public void deletePost(Long userId, Long postId) {
        Post post = getPostOrThrow(postId);
        if (!post.isOwnedBy(userId)) {
            throw new PostException(HttpStatus.FORBIDDEN, "본인이 작성한 게시글만 삭제할 수 있습니다.");
        }
        Long parentId = post.getParentId();
        Long quoteId = post.getQuoteId();
        Long repostId = post.getRepostId();
        postRepository.delete(post);
        decrementCountsOf(parentId, quoteId, repostId);
    }

    public List<Post> getReplies(Long postId) {
        return postRepository.findAllByParentIdOrderByCreatedAtAsc(postId);
    }

    public List<Post> getQuotes(Long postId) {
        return postRepository.findAllByQuoteIdOrderByCreatedAtDesc(postId);
    }

    public List<Post> getReposts(Long postId) {
        return postRepository.findAllByRepostIdOrderByCreatedAtDesc(postId);
    }

    /**
     * 게시글을 삭제할 때 함께 줄여야 하는 부모 카운트를 반영한다.
     *
     * <p>인용글도 작성 시 {@code createQuote}에서 원본의 replyCount를 올리므로, 삭제 시 같은 기준으로 내린다.
     */
    private void decrementCountsOf(Long parentId, Long quoteId, Long repostId) {
        if (parentId != null) {
            postRepository.decrementReplyCount(parentId);
        }
        if (quoteId != null) {
            postRepository.decrementReplyCount(quoteId);
        }
        if (repostId != null) {
            postRepository.decrementRepostCount(repostId);
        }
    }

    /**
     * 리포스트를 대상으로 답글/인용/리포스트/좋아요를 달면 원본을 대상으로 삼는다.
     */
    public Post getNormalizedTarget(Long postId) {
        Post target = getPostOrThrow(postId);
        if (target.isRepost()) {
            return getPostOrThrow(target.getRepostId());
        }
        return target;
    }

    private Post getPostOrThrow(Long postId) {
        return postRepository.findById(postId)
                .orElseThrow(() -> new PostException(HttpStatus.NOT_FOUND, "존재하지 않는 게시글입니다."));
    }

    private User getUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new PostException(HttpStatus.NOT_FOUND, "존재하지 않는 사용자입니다."));
    }

    private String validateContent(String content) {
        String trimmed = content == null ? "" : content.trim();
        if (trimmed.isEmpty()) {
            throw new PostException(HttpStatus.BAD_REQUEST, "본문은 필수입니다.");
        }
        if (trimmed.length() > Post.MAX_CONTENT_LENGTH) {
            throw new PostException(HttpStatus.BAD_REQUEST,
                    "본문은 " + Post.MAX_CONTENT_LENGTH + "자 이하로 입력해주세요.");
        }
        return trimmed;
    }
}
