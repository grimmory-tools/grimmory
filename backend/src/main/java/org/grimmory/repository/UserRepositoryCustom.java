package org.grimmory.repository;

import org.grimmory.model.entity.BookLoreUserEntity;

import java.util.List;
import java.util.Optional;

public interface UserRepositoryCustom {

    Optional<BookLoreUserEntity> findByIdWithDetails(Long id);

    List<BookLoreUserEntity> findAllWithDetails();

    Optional<BookLoreUserEntity> findByIdWithSettings(Long id);

    Optional<BookLoreUserEntity> findByIdWithLibraries(Long id);

    Optional<BookLoreUserEntity> findByIdWithPermissions(Long id);
}
