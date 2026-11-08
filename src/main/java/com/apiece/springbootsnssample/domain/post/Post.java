package com.apiece.springbootsnssample.domain.post;

import com.apiece.springbootsnssample.common.entity.BaseEntity;
import com.apiece.springbootsnssample.domain.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.ConstraintMode;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.io.Serializable;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

/**
 * 게시글. 게시글/답글/인용글/리포스트를 한 테이블에서 표현하고, 어떤 대상 컬럼이 채워졌는지로 종류를 구분한다.
 *
 * <ul>
 *   <li>{@code parentId} 가 있으면 답글</li>
 *   <li>{@code quoteId} 가 있으면 인용글</li>
 *   <li>{@code repostId} 가 있으면 리포스트 (본문 없음, {@code content} 는 null)</li>
 * </ul>
 */
@Entity
@Table(name = "posts")
@SQLDelete(sql = "update posts set deleted_at = now(), updated_at = now() where id = ?")
@SQLRestriction("deleted_at is null")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Post extends BaseEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    public static final int MAX_CONTENT_LENGTH = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // DB FK 제약은 두지 않는다.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private User user;

    @Column(length = MAX_CONTENT_LENGTH)
    private String content;

    private Long parentId;

    private Long quoteId;

    private Long repostId;

    @Column(nullable = false)
    private int replyCount;

    @Column(nullable = false)
    private int repostCount;

    @Column(nullable = false)
    private int likeCount;

    @Column(nullable = false)
    private long viewCount;

    public static Post createPost(User user, String content) {
        return Post.builder()
                .user(user)
                .content(content)
                .build();
    }

    public static Post createReply(User user, String content, Long parentId) {
        return Post.builder()
                .user(user)
                .content(content)
                .parentId(parentId)
                .build();
    }

    public static Post createQuote(User user, String content, Long quoteId) {
        return Post.builder()
                .user(user)
                .content(content)
                .quoteId(quoteId)
                .build();
    }

    public static Post createRepost(User user, Long repostId) {
        return Post.builder()
                .user(user)
                .repostId(repostId)
                .build();
    }

    public boolean isReply() {
        return parentId != null;
    }

    public boolean isQuote() {
        return quoteId != null;
    }

    public boolean isRepost() {
        return repostId != null;
    }

    public boolean isOwnedBy(Long userId) {
        return userId != null && user != null && userId.equals(user.getId());
    }
}
