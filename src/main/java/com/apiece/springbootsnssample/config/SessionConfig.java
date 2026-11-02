package com.apiece.springbootsnssample.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.session.security.SpringSessionBackedSessionRegistry;

/**
 * Redis 세션 저장소 기반 SessionRegistry 구성.
 *
 * <p>동시 세션 수 집계를 JVM 메모리가 아닌 Redis(principal name 인덱스)에서 수행하므로
 * 여러 애플리케이션 인스턴스에서도 동일한 정책이 적용된다.
 */
@Configuration
public class SessionConfig {

    @Bean
    public <S extends Session> SessionRegistry sessionRegistry(FindByIndexNameSessionRepository<S> sessionRepository) {
        return new SpringSessionBackedSessionRegistry<>(sessionRepository);
    }
}
