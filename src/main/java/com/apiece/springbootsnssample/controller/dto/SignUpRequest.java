package com.apiece.springbootsnssample.controller.dto;

import com.apiece.springbootsnssample.domain.user.User;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 회원가입 요청 DTO.
 *
 * <p>컨트롤러에서 요청 본문(JSON)을 바인딩하며, Bean Validation으로 입력값을 검증한다.
 * password는 평문 상태로 전달되고, 인코딩은 유스케이스인 {@code UserService}에서 수행한다.
 *
 * @param username 로그인에 사용되는 고유 아이디 (필수, 50자 이하)
 * @param password 평문 비밀번호 (필수, 저장 전 Argon2id로 인코딩됨)
 */
public record SignUpRequest(
        @NotBlank(message = "username은 필수입니다.")
        @Size(max = 50, message = "username은 50자 이하로 입력해주세요.")
        String username,

        @NotBlank(message = "password는 필수입니다.")
        String password
) {

    /**
     * 요청값을 {@link User} 엔티티로 변환한다.
     *
     * <p>password는 평문 그대로 담으며, 실제 인코딩은 {@code UserService.signup()}에서
     * PasswordEncoder(Argon2id)를 통해 수행된다.
     *
     * @return id가 null인 미저장 User 엔티티
     */
    public User toEntity() {
        return new User(username, password);
    }
}
