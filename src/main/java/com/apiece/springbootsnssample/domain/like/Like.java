package com.apiece.springbootsnssample.domain.like;

import com.apiece.springbootsnssample.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
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
 * 게시글 좋아요.
 *
 * <p>엔티티 이름은 JPQL의 {@code like} 연산자와 겹치지 않도록 {@code PostLike}로 둔다.
 */
@Entity(name = "PostLike")
@Table(name = "likes")
// (user_id, post_id) 유니크는 삭제된 행을 제외한 부분 유니크 인덱스로 ddl.sql에서 관리한다.
@SQLDelete(sql = "update likes set deleted_at = now(), updated_at = now() where id = ?")
@SQLRestriction("deleted_at is null")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Like extends BaseEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private Long postId;

    public Like(Long userId, Long postId) {
        this(null, userId, postId);
    }
}
