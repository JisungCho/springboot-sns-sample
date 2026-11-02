package com.apiece.springbootsnssample.security;

import com.apiece.springbootsnssample.common.exception.ErrorResponse;
import tools.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.session.SessionAuthenticationException;

/**
 * 로그인 실패 시 /login?error 리다이렉트 대신 401 JSON을 응답하는 핸들러.
 *
 * <p>예외 종류(BadCredentialsException 등)와 무관하게 동일한 메시지로 통일해
 * username 존재 여부를 노출하지 않는다.
 */
@RequiredArgsConstructor
public class JsonAuthenticationFailureHandler implements AuthenticationFailureHandler {

    private static final String AUTHENTICATION_FAILED_MESSAGE = "아이디 또는 비밀번호가 올바르지 않습니다.";

    private static final String SESSION_LIMIT_EXCEEDED_MESSAGE = "허용된 동시 접속 수를 초과하여 로그인할 수 없습니다.";

    private final ObjectMapper objectMapper;

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
                                        AuthenticationException exception) throws IOException {
        // 비밀번호는 맞지만 동시 세션 수 초과로 로그인이 차단된 경우 (maxSessionsPreventsLogin=true)
        boolean sessionLimitExceeded = exception instanceof SessionAuthenticationException;
        response.setStatus(sessionLimitExceeded ? HttpStatus.CONFLICT.value() : HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getWriter(), ErrorResponse.from(
                sessionLimitExceeded ? SESSION_LIMIT_EXCEEDED_MESSAGE : AUTHENTICATION_FAILED_MESSAGE));
    }
}
