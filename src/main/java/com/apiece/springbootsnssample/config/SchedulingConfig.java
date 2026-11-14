package com.apiece.springbootsnssample.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 주기 작업 활성화. 게시글 조회수 Redis → DB 반영 스케줄러가 여기에 의존한다.
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
