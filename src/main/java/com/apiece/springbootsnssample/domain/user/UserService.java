package com.apiece.springbootsnssample.domain.user;

import com.apiece.springbootsnssample.domain.followcount.FollowCountService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final FollowCountService followCountService;

    @Transactional
    public User signup(User user) {
        validateDuplicateUsername(user.getUsername());
        user.encodePassword(passwordEncoder.encode(user.getPassword()));
        User saved = userRepository.save(user);
        followCountService.create(saved.getId());
        return saved;
    }

    private void validateDuplicateUsername(String username) {
        if (userRepository.existsByUsername(username)) {
            throw new UserException(HttpStatus.CONFLICT, "이미 사용 중인 username입니다: " + username);
        }
    }
}
