package com.apiece.springbootsnssample.domain.post;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class PostViewCountFlushSchedulerTest {

    @Mock
    private PostViewCountService postViewCountService;

    @InjectMocks
    private PostViewCountFlushScheduler scheduler;

    @Test
    @DisplayName("스케줄 실행 시 서비스의 flush를 호출한다")
    void flush_view_counts_delegates_to_service() {
        // given
        given(postViewCountService.flush()).willReturn(1);

        // when
        scheduler.flushViewCounts();

        // then
        then(postViewCountService).should().flush();
    }

    @Test
    @DisplayName("반영된 게시글이 없어도 정상 종료한다")
    void flush_view_counts_with_nothing_to_flush() {
        // given
        given(postViewCountService.flush()).willReturn(0);

        // when
        scheduler.flushViewCounts();

        // then
        then(postViewCountService).should().flush();
        then(postViewCountService).should(never()).increaseViewCount(org.mockito.ArgumentMatchers.anyLong());
    }
}
