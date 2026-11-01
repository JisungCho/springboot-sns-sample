package com.apiece.springbootsnssample.domain.user;

import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class AuthUserDetailsServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private AuthUserDetailsService userDetailsService;

    @Test
    @DisplayName("username으로 사용자를 조회하면 UserPrincipal을 반환한다")
    void loadUserByUsername_returns_user_principal() {
        // given
        User user = User.builder().id(1L).username("user1").password("encoded").build();
        given(userRepository.findByUsername("user1")).willReturn(Optional.of(user));

        // when
        UserDetails userDetails = userDetailsService.loadUserByUsername("user1");

        // then
        assertThat(userDetails).isInstanceOf(UserPrincipal.class);
        assertThat(userDetails.getUsername()).isEqualTo("user1");
        assertThat(userDetails.getPassword()).isEqualTo("encoded");
        assertThat(userDetails.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_USER");
    }

    @Test
    @DisplayName("존재하지 않는 username이면 UsernameNotFoundException이 발생한다")
    void loadUserByUsername_not_found_throws() {
        // given
        given(userRepository.findByUsername("ghost")).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> userDetailsService.loadUserByUsername("ghost"))
                .isInstanceOf(UsernameNotFoundException.class)
                .hasMessageContaining("ghost");
    }
}
