package org.grimmory.repository;

import org.grimmory.model.entity.BookLoreUserEntity;
import org.grimmory.model.entity.RefreshTokenEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshTokenEntity, Long> {
    Optional<RefreshTokenEntity> findByToken(String token);
    List<RefreshTokenEntity> findAllByUserAndRevokedFalse(BookLoreUserEntity user);
    void deleteByUser(BookLoreUserEntity user);
}
