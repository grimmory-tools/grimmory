package com.adityachandel.booklore.repository;

import com.adityachandel.booklore.model.entity.BookLoreUserEntity;
import com.adityachandel.booklore.model.enums.ProvisioningMethod;
import org.jspecify.annotations.NonNull;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<BookLoreUserEntity, Long> {

    Optional<BookLoreUserEntity> findByUsername(String username);

    Optional<BookLoreUserEntity> findByEmail(String email);

    Optional<BookLoreUserEntity> findById(@NonNull Long id);

    long countByProvisioningMethod(ProvisioningMethod provisioningMethod);
}

