package org.grimmory.repository;

import org.grimmory.model.entity.BookShelfKey;
import org.grimmory.model.entity.BookShelfMapping;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BookShelfMappingRepository extends JpaRepository<BookShelfMapping, BookShelfKey> {
}