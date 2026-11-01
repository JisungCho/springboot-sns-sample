package com.apiece.springbootsnssample.domain.user;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * form 로그인 인증 시 사용자를 DB에서 조회하는 {@link UserDetailsService}.
 *
 * <p>UserDetailsService 빈이 존재하면 Spring Security가 DaoAuthenticationProvider를
 * 자동 구성하여, loadUserByUsername으로 조회한 사용자의 password와 입력값을
 * PasswordEncoder(Argon2id)로 검증한다.
 */
@Service
@RequiredArgsConstructor
public class AuthUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("존재하지 않는 사용자입니다: " + username));
        return new UserPrincipal(user);
    }
}