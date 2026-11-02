package com.apiece.springbootsnssample.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 동시 세션 제어 정책 (app.session.*).
 *
 * @param maximumSessions            사용자당 허용 동시 세션 수 (1 이상, 기본 1)
 * @param maxSessionsPreventsLogin   true: 초과 시 신규 로그인 차단, false: 가장 오래된 세션 만료 (기본 false)
 */
@ConfigurationProperties(prefix = "app.session")
public record SessionPolicyProperties(
        @DefaultValue("1") int maximumSessions,
        @DefaultValue("false") boolean maxSessionsPreventsLogin
) {

    public SessionPolicyProperties {
        if (maximumSessions < 1) {
            throw new IllegalArgumentException("app.session.maximum-sessions는 1 이상이어야 합니다: " + maximumSessions);
        }
    }
}
