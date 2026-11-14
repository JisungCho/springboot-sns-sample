package com.apiece.springbootsnssample.domain.post;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 게시글 조회수를 Redis에 모아 두었다가 DB로 반영하는 서비스.
 *
 * <p>조회 요청마다 DB를 UPDATE하면 인기 글에 요청이 몰릴 때 같은 행의 잠금 경합이 생기므로,
 * 조회수는 Redis 카운터({@code post:view:{postId}})에만 증가시키고 변경된 postId를
 * {@code post:view:dirty_set}에 모은다. 실제 {@code view_count} 컬럼 반영은
 * {@link PostViewCountFlushScheduler}가 주기적으로 {@link #flush()}를 호출해 처리한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PostViewCountService {

    private static final String VIEW_COUNT_KEY_PREFIX = "post:view:";
    private static final String DIRTY_SET_KEY = "post:view:dirty_set";

    private final StringRedisTemplate redisTemplate;
    private final PostRepository postRepository;

    /**
     * 게시글 조회수를 Redis에서 1 증가시키고, DB 반영 대상으로 표시한다.
     *
     * <p>존재하지 않는 게시글이 dirty_set에 쌓이지 않도록 존재 여부는 여기서 확인한다.
     */
    public void increaseViewCount(Long postId) {
        if (!postRepository.existsById(postId)) {
            throw new PostException(HttpStatus.NOT_FOUND, "존재하지 않는 게시글입니다.");
        }
        redisTemplate.opsForValue().increment(viewCountKey(postId));
        redisTemplate.opsForSet().add(DIRTY_SET_KEY, String.valueOf(postId));
    }

    /**
     * dirty_set에 있는 게시글의 Redis 조회수를 DB로 옮긴다.
     *
     * <p>여러 인스턴스가 동시에 실행될 수 있으므로 {@code SREM}으로 성공한(1을 반환한) postId만 처리한다.
     * 처리 대상은 {@code GETDEL}로 한 번에 읽고 지워서, 반영 도중 들어온 조회가 유실되거나 중복 반영되지 않게 한다.
     *
     * @return DB에 반영한 게시글 수
     */
    @Transactional
    public int flush() {
        Set<String> dirtyPostIds = redisTemplate.opsForSet().members(DIRTY_SET_KEY);
        if (dirtyPostIds == null || dirtyPostIds.isEmpty()) {
            return 0;
        }

        Map<Long, Long> claimedCounts = new LinkedHashMap<>();
        Set<String> claimedPostIds = new LinkedHashSet<>();
        try {
            claim(dirtyPostIds, claimedPostIds, claimedCounts);
            claimedCounts.forEach((postId, delta) -> postRepository.incrementViewCountBy(postId, delta));
        } catch (RuntimeException e) {
            // Redis에서 지웠거나 DB에 반영하지 못한 값을 되돌려 다음 주기에 다시 시도한다.
            restore(claimedPostIds, claimedCounts);
            throw e;
        }
        return claimedCounts.size();
    }

    /**
     * dirty_set에서 postId를 선점(SREM)하고 해당 카운터를 읽은 뒤 지운다.
     *
     * <p>카운터가 이미 없거나 0이면 반영할 값이 없으므로 {@code claimedCounts}에는 넣지 않는다.
     * 선점한 postId는 실패 시 되돌릴 수 있도록 {@code claimedPostIds}에 모두 남긴다.
     */
    private void claim(Set<String> dirtyPostIds, Set<String> claimedPostIds, Map<Long, Long> claimedCounts) {
        for (String rawPostId : dirtyPostIds) {
            Long postId = parsePostId(rawPostId);
            if (postId == null) {
                redisTemplate.opsForSet().remove(DIRTY_SET_KEY, rawPostId);
                log.warn("dirty_set에 숫자가 아닌 값이 있어 제거했습니다: {}", rawPostId);
                continue;
            }
            Long removed = redisTemplate.opsForSet().remove(DIRTY_SET_KEY, rawPostId);
            if (removed == null || removed == 0) {
                // 다른 인스턴스가 먼저 가져갔다.
                continue;
            }
            claimedPostIds.add(rawPostId);
            long delta = parseCount(redisTemplate.opsForValue().getAndDelete(viewCountKey(postId)));
            if (delta > 0) {
                claimedCounts.put(postId, delta);
            }
        }
    }

    private void restore(Set<String> claimedPostIds, Map<Long, Long> claimedCounts) {
        claimedPostIds.forEach(rawPostId -> {
            try {
                redisTemplate.opsForSet().add(DIRTY_SET_KEY, rawPostId);
            } catch (RuntimeException e) {
                log.error("게시글 {}을(를) dirty_set에 되돌리지 못했습니다. 다음 조회 전까지 DB에 반영되지 않습니다.",
                        rawPostId, e);
            }
        });
        claimedCounts.forEach((postId, delta) -> {
            try {
                redisTemplate.opsForValue().increment(viewCountKey(postId), delta);
            } catch (RuntimeException e) {
                // 복구까지 실패하면 이번 증가분은 유실된다. 원인 파악을 위해 값과 함께 남긴다.
                log.error("게시글 {} 조회수 {}건을 Redis에 되돌리지 못했습니다.", postId, delta, e);
            }
        });
    }

    private static String viewCountKey(Long postId) {
        return VIEW_COUNT_KEY_PREFIX + postId;
    }

    private static Long parsePostId(String rawPostId) {
        try {
            return Long.valueOf(rawPostId);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static long parseCount(String rawCount) {
        if (rawCount == null) {
            return 0L;
        }
        try {
            return Long.parseLong(rawCount);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }
}
