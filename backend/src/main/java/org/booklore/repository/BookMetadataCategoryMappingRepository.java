package org.booklore.repository;

import org.booklore.model.entity.BookMetadataCategoryKey;
import org.booklore.model.entity.BookMetadataCategoryMapping;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Set;

@Repository
public interface BookMetadataCategoryMappingRepository extends JpaRepository<BookMetadataCategoryMapping, BookMetadataCategoryKey> {

    List<BookMetadataCategoryMapping> findAllByBookIdIn(Set<Long> bookIds);
}
