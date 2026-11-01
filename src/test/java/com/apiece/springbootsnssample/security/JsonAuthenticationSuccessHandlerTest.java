package com.apiece.springbootsnssample.security;

import tools.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;

import static org.assertj.core.api.Assertions.assertThat;

class JsonAuthenticationSuccessHandlerTest {

    private final JsonMapper objectMapper = JsonMapper.builder().build();
    private final JsonAuthenticationSuccessHandler handler = new JsonAuthenticationSuccessHandler(objectMapper);

    @Test
    @DisplayName("로그인 성공 시 200과 username JSON을 응답한다")
    void returns_200_with_username_json() throws Exception {
        // given
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        Authentication authentication = UsernamePasswordAuthenticationToken.authenticated(
                "user1", "password", AuthorityUtils.createAuthorityList("ROLE_USER"));

        // when
        handler.onAuthenticationSuccess(request, response, authentication);

        // then
        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK.value());
        assertThat(response.getContentType()).startsWith(MediaType.APPLICATION_JSON_VALUE);
        LoginResponse body = objectMapper.readValue(response.getContentAsString(), LoginResponse.class);
        assertThat(body.username()).isEqualTo("user1");
    }
}
