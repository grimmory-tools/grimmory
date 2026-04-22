package org.booklore.repository;

import org.booklore.model.entity.OpdsUserV2Entity;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface OpdsUserV2Repository extends JpaRepository<OpdsUserV2Entity, Long> {

    Optional<OpdsUserV2Entity> findByUsername(String username);

    List<OpdsUserV2Entity> findByUserId(Long userId);

    @EntityGraph(attributePaths = {"user"})
    @Query("SELECT o FROM OpdsUserV2Entity o WHERE o.id = :id")
    Optional<OpdsUserV2Entity> findByIdWithUser(@Param("id") Long id);
}
