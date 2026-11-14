package com.apiece.springbootsnssample.domain.post;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Redis에 쌓인 게시글 조회수를 매분 DB에 반영하는 스케줄러.
 *
 * <p>스케줄러 스레드가 하나라 이전 실행이 끝나기 전에 다음 실행이 겹치지 않고 순서대로 대기한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PostViewCountFlushScheduler {

    private final PostViewCountService postViewCountService;

    @Scheduled(cron = "${app.post-view.flush-cron:0 * * * * *}")
    public void flushViewCounts() {
        int flushedPostCount = postViewCountService.flush();
        if (flushedPostCount > 0) {
            log.info("게시글 조회수 {}건을 DB에 반영했습니다.", flushedPostCount);
        }
    }
}
