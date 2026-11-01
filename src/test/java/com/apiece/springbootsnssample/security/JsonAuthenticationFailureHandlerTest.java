package com.apiece.springbootsnssample.security;

import com.apiece.springbootsnssample.common.exception.ErrorResponse;
import tools.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;

import static org.assertj.core.api.Assertions.assertThat;

class JsonAuthenticationFailureHandlerTest {

    private final JsonMapper objectMapper = JsonMapper.builder().build();
    private final JsonAuthenticationFailureHandler handler = new JsonAuthenticationFailureHandler(objectMapper);

    @Test
    @DisplayName("로그인 실패 시 401과 에러 메시지 JSON을 응답한다")
    void returns_401_with_error_message_json() throws Exception {
        // given
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        // when
        handler.onAuthenticationFailure(request, response, new BadCredentialsException("Bad credentials"));

        // then
        assertThat(response.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
        assertThat(response.getContentType()).startsWith(MediaType.APPLICATION_JSON_VALUE);
        ErrorResponse body = objectMapper.readValue(response.getContentAsString(), ErrorResponse.class);
        assertThat(body.message()).isEqualTo("아이디 또는 비밀번호가 올바르지 않습니다.");
    }
}
