package org.grimmory.repository;

import org.booklore.model.entity.BookLoreUserEntity;
import org.booklore.model.enums.ProvisioningMethod;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<BookLoreUserEntity, Long>, UserRepositoryCustom {

    Optional<BookLoreUserEntity> findByUsername(String username);

    Optional<BookLoreUserEntity> findByEmail(String email);

    long countByProvisioningMethod(ProvisioningMethod provisioningMethod);

    Optional<BookLoreUserEntity> findByOidcIssuerAndOidcSubject(String oidcIssuer, String oidcSubject);
}

