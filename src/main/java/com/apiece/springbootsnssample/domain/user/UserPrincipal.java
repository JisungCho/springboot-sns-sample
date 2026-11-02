package com.apiece.springbootsnssample.domain.user;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * {@link User} 엔티티를 Spring Security의 {@link UserDetails}로 감싸는 어댑터.
 *
 * <p>form 로그인 시 DaoAuthenticationProvider가 이 객체의 password와 사용자가 입력한
 * 평문 비밀번호를 PasswordEncoder로 검증하고, 성공하면 이 객체가 세션의
 * SecurityContext에 저장된다.
 */
@Getter
@RequiredArgsConstructor
public class UserPrincipal implements UserDetails, Serializable {

    private static final long serialVersionUID = 1L;

    private static final String DEFAULT_ROLE = "ROLE_USER";

    private final User user;

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority(DEFAULT_ROLE));
    }

    @Override
    public String getPassword() {
        return user.getPassword();
    }

    @Override
    public String getUsername() {
        return user.getUsername();
    }
}