package org.booklore.repository;

import org.booklore.model.entity.WantedBookEntity;
import org.booklore.model.enums.WantedBookStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WantedBookRepository extends JpaRepository<WantedBookEntity, Long> {

    List<WantedBookEntity> findByStatus(WantedBookStatus status);

    List<WantedBookEntity> findByStatusIn(List<WantedBookStatus> statuses);

    Optional<WantedBookEntity> findByIsbn13(String isbn13);

    boolean existsByIsbn13(String isbn13);

    boolean existsByTitleIgnoreCaseAndAuthorIgnoreCase(String title, String author);
}
