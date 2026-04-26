package org.booklore.repository;

import org.booklore.model.entity.BookShelfKey;
import org.booklore.model.entity.BookShelfMapping;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BookShelfMappingRepository extends JpaRepository<BookShelfMapping, BookShelfKey> {
}