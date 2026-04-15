package org.booklore.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.booklore.model.entity.BookLoreUserEntity;
import org.hibernate.Hibernate;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

public class UserRepositoryImpl implements UserRepositoryCustom {

    @PersistenceContext
    private EntityManager em;

    @Override
    @Transactional(readOnly = true)
    public Optional<BookLoreUserEntity> findByIdWithDetails(Long id) {
        List<BookLoreUserEntity> users = em.createQuery(
                        "SELECT DISTINCT u FROM BookLoreUserEntity u " +
                                "LEFT JOIN FETCH u.settings " +
                                "LEFT JOIN FETCH u.permissions " +
                                "WHERE u.id = :id",
                        BookLoreUserEntity.class)
                .setParameter("id", id)
                .getResultList();
        if (users.isEmpty()) return Optional.empty();

        BookLoreUserEntity user = users.getFirst();
        Hibernate.initialize(user.getLibraries());
        user.getLibraries().forEach(lib -> Hibernate.initialize(lib.getLibraryPaths()));

        return Optional.of(user);
    }

    @Override
    @Transactional(readOnly = true)
    public List<BookLoreUserEntity> findAllWithDetails() {
        List<BookLoreUserEntity> users = em.createQuery(
                        "SELECT DISTINCT u FROM BookLoreUserEntity u " +
                                "LEFT JOIN FETCH u.settings " +
                                "LEFT JOIN FETCH u.permissions",
                        BookLoreUserEntity.class)
                .getResultList();
        if (users.isEmpty()) return users;

        users.forEach(user -> {
            Hibernate.initialize(user.getLibraries());
            user.getLibraries().forEach(lib -> Hibernate.initialize(lib.getLibraryPaths()));
        });

        return users;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<BookLoreUserEntity> findByIdWithSettings(Long id) {
        BookLoreUserEntity user = em.find(BookLoreUserEntity.class, id);
        if (user == null) return Optional.empty();
        Hibernate.initialize(user.getSettings());
        Hibernate.initialize(user.getPermissions());
        return Optional.of(user);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<BookLoreUserEntity> findByIdWithLibraries(Long id) {
        BookLoreUserEntity user = em.find(BookLoreUserEntity.class, id);
        if (user == null) return Optional.empty();
        Hibernate.initialize(user.getLibraries());
        user.getLibraries().forEach(lib -> Hibernate.initialize(lib.getLibraryPaths()));
        Hibernate.initialize(user.getPermissions());
        return Optional.of(user);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<BookLoreUserEntity> findByIdWithPermissions(Long id) {
        BookLoreUserEntity user = em.find(BookLoreUserEntity.class, id);
        if (user == null) return Optional.empty();
        Hibernate.initialize(user.getPermissions());
        return Optional.of(user);
    }
}
