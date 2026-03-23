package org.booklore.repository;

import org.booklore.model.entity.BookLoreUserEntity;
import org.booklore.model.enums.ProvisioningMethod;
import org.jspecify.annotations.NonNull;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<BookLoreUserEntity, Long> {

    Optional<BookLoreUserEntity> findByUsername(String username);

    Optional<BookLoreUserEntity> findByEmail(String email);

    Optional<BookLoreUserEntity> findById(@NonNull Long id);

    /**
     * Fetch a user with all eagerly-needed associations (settings, libraries, permissions)
     * in a single query to prevent N+1 problems after OSIV is disabled.
     */
    @EntityGraph(attributePaths = {"settings", "libraries", "libraries.libraryPaths", "permissions"})
    @Query("SELECT u FROM BookLoreUserEntity u WHERE u.id = :id")
    Optional<BookLoreUserEntity> findByIdWithDetails(@Param("id") Long id);

    /**
     * Fetch a user with settings and permissions (without libraries) for settings-related operations.
     */
    @EntityGraph(attributePaths = {"settings", "permissions"})
    @Query("SELECT u FROM BookLoreUserEntity u WHERE u.id = :id")
    Optional<BookLoreUserEntity> findByIdWithSettings(@Param("id") Long id);

    /**
     * Fetch a user with libraries and permissions for authorization/library-access operations.
     */
    @EntityGraph(attributePaths = {"libraries", "libraries.libraryPaths", "permissions"})
    @Query("SELECT u FROM BookLoreUserEntity u WHERE u.id = :id")
    Optional<BookLoreUserEntity> findByIdWithLibraries(@Param("id") Long id);

    /**
     * Fetch a user with just permissions for security checks or simple profile updates.
     */
    @EntityGraph(attributePaths = {"permissions"})
    @Query("SELECT u FROM BookLoreUserEntity u WHERE u.id = :id")
    Optional<BookLoreUserEntity> findByIdWithPermissions(@Param("id") Long id);

    /**
     * Fetch all users with their settings, libraries, and permissions in a single query.
     * Prevents N+1 when listing all users (admin panel).
     */
    @EntityGraph(attributePaths = {"settings", "libraries", "libraries.libraryPaths", "permissions"})
    @Query("SELECT DISTINCT u FROM BookLoreUserEntity u")
    List<BookLoreUserEntity> findAllWithDetails();

    /**
     * Fetch a user by username with all associations needed for authentication/DTO mapping.
     */
    @EntityGraph(attributePaths = {"settings", "libraries", "libraries.libraryPaths", "permissions"})
    @Query("SELECT u FROM BookLoreUserEntity u WHERE u.username = :username")
    Optional<BookLoreUserEntity> findByUsernameWithDetails(@Param("username") String username);

    long countByProvisioningMethod(ProvisioningMethod provisioningMethod);

    Optional<BookLoreUserEntity> findByOidcIssuerAndOidcSubject(String oidcIssuer, String oidcSubject);
}

