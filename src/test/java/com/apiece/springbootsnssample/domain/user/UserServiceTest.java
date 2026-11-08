package com.apiece.springbootsnssample.domain.user;

import com.apiece.springbootsnssample.domain.followcount.FollowCountService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private FollowCountService followCountService;

    @InjectMocks
    private UserService userService;

    @Test
    @DisplayName("회원가입에 성공하면 비밀번호가 인코딩되어 저장된다")
    void signup_encodes_password_and_saves_user() {
        // given
        User user = new User("user1", "raw-password");
        given(userRepository.existsByUsername("user1")).willReturn(false);
        given(passwordEncoder.encode("raw-password")).willReturn("encoded-password");
        given(userRepository.save(any(User.class))).willAnswer(invocation -> {
            User argument = invocation.getArgument(0);
            return User.builder()
                    .id(1L)
                    .username(argument.getUsername())
                    .password(argument.getPassword())
                    .build();
        });

        // when
        User saved = userService.signup(user);

        // then
        assertThat(saved.getId()).isEqualTo(1L);
        assertThat(saved.getUsername()).isEqualTo("user1");
        assertThat(saved.getPassword()).isEqualTo("encoded-password");
        then(userRepository).should().save(user);
        then(followCountService).should().create(1L);
    }

    @Test
    @DisplayName("username이 중복되면 UserException(CONFLICT)이 발생하고 저장하지 않는다")
    void signup_with_duplicate_username_throws_exception() {
        // given
        User user = new User("user1", "raw-password");
        given(userRepository.existsByUsername("user1")).willReturn(true);

        // when
        UserException exception = catchThrowableOfType(
                UserException.class, () -> userService.signup(user));

        // then
        assertThat(exception.getStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(exception.getMessage()).contains("user1");
        then(userRepository).should(never()).save(any(User.class));
        then(followCountService).should(never()).create(any());
    }
}
