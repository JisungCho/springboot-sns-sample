package com.apiece.springbootsnssample.common.entity;

import com.apiece.springbootsnssample.config.JpaConfig;
import com.apiece.springbootsnssample.domain.user.User;
import com.apiece.springbootsnssample.domain.user.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(JpaConfig.class)
class BaseEntityAuditingTest {

    @Autowired
    private UserRepository userRepository;

    @Test
    @DisplayName("저장 시 createdAt과 updatedAt이 자동으로 채워지고 deletedAt은 null이다")
    void persist_fills_createdAt_and_updatedAt() {
        User saved = userRepository.save(new User("user1", "password"));

        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
        assertThat(saved.getDeletedAt()).isNull();
    }

    @Test
    @DisplayName("softDelete 호출 시 deletedAt이 설정된다")
    void softDelete_sets_deletedAt() {
        User saved = userRepository.save(new User("user1", "password"));

        saved.softDelete();
        User updated = userRepository.save(saved);

        assertThat(updated.getDeletedAt()).isNotNull();
    }
}
